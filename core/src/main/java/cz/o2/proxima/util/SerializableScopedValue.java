/**
 * Copyright 2017-2020 O2 Czech Republic, a.s.
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
package cz.o2.proxima.util;

import com.google.common.base.MoreObjects;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A value that holds a serializable value and scopes it value to given context value.
 *
 * @param <C> context type parameter
 * @param <V> type parameter
 */
public final class SerializableScopedValue<C, V extends Serializable> implements Serializable {

  private static final Map<String, Map<Object, Object>> MAP = new ConcurrentHashMap<>();

  private final String uuid = UUID.randomUUID().toString();
  private final V original;

  public SerializableScopedValue(V what) {
    this.original = Objects.requireNonNull(what);
    MAP.putIfAbsent(uuid, new ConcurrentHashMap<>());
  }

  @SuppressWarnings("unchecked")
  public V get(C context) {
    return (V) MAP.get(uuid).computeIfAbsent(context, t -> cloneOriginal());
  }

  private V cloneOriginal() {
    return SerializableUtils.clone(original);
  }

  /** Clear reference for given context and reinitialize it when accessed again. */
  public void reset(C context) {
    MAP.get(uuid).remove(context);
  }

  private Object readResolve() throws ObjectStreamException {
    MAP.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(original, uuid);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof SerializableScopedValue)) {
      return false;
    }
    SerializableScopedValue<?, ?> other = (SerializableScopedValue<?, ?>) obj;
    return other.uuid.equals(uuid) && other.original.equals(original);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("uuid", uuid).add("original", original).toString();
  }
}
