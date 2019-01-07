/**
 * Copyright 2017-2019 O2 Czech Republic, a.s.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cz.o2.proxima.direct.core;

import com.google.common.collect.Iterables;
import cz.o2.proxima.functional.Factory;
import cz.o2.proxima.repository.AttributeDescriptor;
import cz.o2.proxima.repository.AttributeFamilyDescriptor;
import cz.o2.proxima.repository.AttributeFamilyProxyDescriptor;
import cz.o2.proxima.repository.DataOperator;
import cz.o2.proxima.repository.Repository;
import cz.o2.proxima.storage.StorageType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link DataOperator} implementation for direct accesses.
 */
@Slf4j
public class DirectDataOperator implements DataOperator, ContextProvider {

  /** Repository. */
  @Getter
  private final Repository repo;

  /** AttributeFamilyDescriptor with associated DirectAttributeFamilyDescriptor. */
  private final Map<AttributeFamilyDescriptor, DirectAttributeFamilyDescriptor>
      familyMap = Collections.synchronizedMap(new HashMap<>());

  /**
   * Cache of writers for all attributes.
   */
  private final Map<AttributeDescriptor<?>, OnlineAttributeWriter> writers
      = Collections.synchronizedMap(new HashMap<>());


  // FIXME: configurable
  private final Factory<ExecutorService> executorFactory = () -> Executors
      .newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("ProximaRepositoryPool");
        t.setUncaughtExceptionHandler((thr, exc) ->
            log.error("Error running task in thread {}", thr.getName(), exc));
        return t;
      });


  private final Context context;
  private final List<DataAccessorFactory> factories = new ArrayList<>();

  DirectDataOperator(Repository repo) {

    this.repo = repo;
    this.context = new Context(familyMap::get, executorFactory);
    ServiceLoader<DataAccessorFactory> loader = ServiceLoader.load(
        DataAccessorFactory.class);
    Iterables.addAll(factories, loader);
    reload();
  }

  @Override
  public final void reload() {
    close();
    dependencyOrdered(repo.getAllFamilies())
        .forEach(family -> addResolvedFamily(family, factories));
  }

  /**
   * Create list of families ordered by dependencies between them (non-proxy first).
   */
  private List<AttributeFamilyDescriptor> dependencyOrdered(
      Stream<AttributeFamilyDescriptor> families) {

    Set<AttributeFamilyDescriptor> available = new HashSet<>();
    List<AttributeFamilyDescriptor> resolved = new ArrayList<>();
    Set<AttributeFamilyDescriptor> toResolve = families.collect(Collectors.toSet());
    while (!toResolve.isEmpty()) {
      // prevent infinite cycles
      List<AttributeFamilyDescriptor> remove = new ArrayList<>();
      List<AttributeFamilyDescriptor> add = new ArrayList<>();
      toResolve
          .stream()
          .filter(af -> !available.contains(af))
          .forEachOrdered(af -> {
            if (!af.isProxy()) {
              available.add(af);
              resolved.add(af);
              remove.add(af);
            } else {
              AttributeFamilyProxyDescriptor proxy = af.toProxy();
              if (available.contains(proxy.getTargetFamilyRead())
                  && available.contains(proxy.getTargetFamilyWrite())) {

                available.add(af);
                resolved.add(af);
                remove.add(af);
              } else if (!available.contains(proxy.getTargetFamilyRead())) {
                add.add(proxy.getTargetFamilyRead());
              } else {
                add.add(proxy.getTargetFamilyWrite());
              }
            }
          });
      if (add.isEmpty() && remove.isEmpty()) {
        throw new IllegalStateException(
            "Cannot make progress in resolving families "
                + toResolve.stream()
                    .map(AttributeFamilyDescriptor::getName)
                    .collect(Collectors.toList())
                + ", currently resolved "
                + available.stream()
                    .map(AttributeFamilyDescriptor::getName)
                    .collect(Collectors.toList()));
      }
      add.forEach(toResolve::add);
      remove.forEach(toResolve::remove);
    }
    return resolved;
  }

  private void addResolvedFamily(
      AttributeFamilyDescriptor family,
      List<DataAccessorFactory> factories) {

    if (familyMap.get(family) == null) {
      if (family.isProxy()) {
        AttributeFamilyProxyDescriptor proxy = family.toProxy();
        familyMap.put(family, DirectAttributeFamilyProxyDescriptor.of(
            context, proxy));
        addResolvedFamily(proxy.getTargetFamilyRead(), factories);
        addResolvedFamily(proxy.getTargetFamilyWrite(), factories);
      } else {
        DataAccessor accessor = findFor(family, factories);
        familyMap.put(family, new DirectAttributeFamilyDescriptor(
            family, context, accessor));
      }
    }
  }

  private DataAccessor findFor(
      AttributeFamilyDescriptor desc, List<DataAccessorFactory> factories) {

    for (DataAccessorFactory daf : factories) {
      if (daf.accepts(desc.getStorageUri())) {
        return daf.create(desc.getEntity(), desc.getStorageUri(), desc.getCfg());
      }
    }
    throw new IllegalStateException(
        "No DataAccessor for URI " + desc.getStorageUri()
            + " found. You might be missing some dependency.");
  }

  /**
   * Retrieve {@link Context} that is used in all distributed operations.
   * @return the serializable context
   */
  @Override
  public Context getContext() {
    return context;
  }

  /**
   * Convert given core family to direct representation.
   * @param family the family to convert
   * @return the converted family
   */
  public DirectAttributeFamilyDescriptor resolveRequired(
      AttributeFamilyDescriptor family) {
    return context.resolveRequired(family);
  }

  /**
   * Optionally convert given family to direct representation.
   * @param family the family to convert
   * @return the optionally converted family
   */
  public Optional<DirectAttributeFamilyDescriptor> resolve(
      AttributeFamilyDescriptor family) {

    return context.resolve(family);
  }



  public Optional<OnlineAttributeWriter> getWriter(AttributeDescriptor<?> attr) {
    synchronized (writers) {
      OnlineAttributeWriter writer = writers.get(attr);
      if (writer == null) {
        repo.getFamiliesForAttribute(attr)
            .stream()
            .filter(af -> af.getType() == StorageType.PRIMARY)
            .filter(af -> !af.getAccess().isReadonly())
            .findAny()
            .flatMap(context::resolve)
            .ifPresent(af ->
              // store writer of this family to all attributes
              af.getWriter()
                  .ifPresent(w ->
                      af.getAttributes().forEach(a -> writers.put(a, w.online()))));

        return Optional.ofNullable(writers.get(attr));
      }
      return Optional.of(writer);
    }
  }

  @Override
  public void close() {
    synchronized (writers) {
      writers.entrySet().stream().map(Map.Entry::getValue)
          .distinct()
          .forEach(OnlineAttributeWriter::close);
      writers.clear();
    }
    familyMap.clear();
  }

  /**
   * Resolve all direct attribute representations of given attribute.
   * @param desc descriptor of attribute
   * @return the set of all direct attribute representations
   */
  public Set<DirectAttributeFamilyDescriptor> getFamiliesForAttribute(
      AttributeDescriptor<?> desc) {

    return repo.getFamiliesForAttribute(desc)
        .stream()
        .map(this::resolveRequired)
        .collect(Collectors.toSet());
  }

  public Stream<DirectAttributeFamilyDescriptor> getAllFamilies() {
    return repo.getAllFamilies().map(this::resolveRequired);
  }

}
