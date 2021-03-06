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
package cz.o2.proxima.direct.blob;

import static org.junit.Assert.*;

import com.google.common.collect.Lists;
import com.typesafe.config.ConfigFactory;
import cz.o2.proxima.direct.batch.BatchLogObserver;
import cz.o2.proxima.direct.blob.TestBlobStorageAccessor.BlobReader;
import cz.o2.proxima.direct.blob.TestBlobStorageAccessor.BlobWriter;
import cz.o2.proxima.direct.core.Context;
import cz.o2.proxima.direct.core.DirectDataOperator;
import cz.o2.proxima.direct.core.Partition;
import cz.o2.proxima.repository.AttributeDescriptor;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.repository.Repository;
import cz.o2.proxima.storage.StreamElement;
import cz.o2.proxima.util.Pair;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

/** Test suite for {@link BlobLogObservableTest}. */
public class BlobLogObservableTest {

  private final Repository repo = Repository.of(ConfigFactory.load("test-reference.conf"));
  private final EntityDescriptor gateway =
      repo.findEntity("gateway")
          .orElseThrow(() -> new IllegalStateException("Missing entity gateway"));
  private final AttributeDescriptor<byte[]> status = gateway.getAttribute("status");
  private final Context context = repo.getOrCreateOperator(DirectDataOperator.class).getContext();

  private TestBlobStorageAccessor accessor;

  @Before
  public void setUp() {
    accessor =
        new TestBlobStorageAccessor(
            gateway, URI.create("blob-test://bucket/path"), Collections.emptyMap());
  }

  @Test
  public void testListPartitions() throws InterruptedException {
    List<Pair<Long, Long>> stamps =
        Lists.newArrayList(
            Pair.of(1234566000000L, 1234566000000L + 3_600_000L),
            Pair.of(1234566000000L + 3_600_000L, (1234566000000L + 2 * 3_600_000L)));
    writePartitions(
        stamps.stream().map(p -> (p.getSecond() + p.getFirst()) / 2).collect(Collectors.toList()));
    BlobReader observable = accessor.new BlobReader(context);
    List<Partition> partitions = observable.getPartitions();
    assertEquals("Expected single partitions, got " + partitions, 1, partitions.size());
    assertEquals((long) stamps.get(0).getFirst(), partitions.get(0).getMinTimestamp());
    assertEquals((long) stamps.get(1).getSecond(), partitions.get(0).getMaxTimestamp());
  }

  @Test
  public void testObservePartitions() throws InterruptedException {
    List<Pair<Long, Long>> stamps =
        Lists.newArrayList(
            Pair.of(1234566000000L, 1234566000000L + 3_600_000L),
            Pair.of(1234566000000L + 3_600_000L, (1234566000000L + 2 * 3_600_000L)));
    writePartitions(
        stamps.stream().map(p -> (p.getSecond() + p.getFirst()) / 2).collect(Collectors.toList()));
    BlobReader observable = accessor.new BlobReader(context);
    List<StreamElement> observed = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    observable.observe(
        observable.getPartitions(),
        Lists.newArrayList(status),
        new BatchLogObserver() {
          @Override
          public boolean onNext(StreamElement element) {
            observed.add(element);
            return true;
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        });
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    assertEquals(2, observed.size());
  }

  @Test
  public void testObservePartitionsReaderException() throws InterruptedException {
    writePartitions(Arrays.asList(0L, 1L, 2L));
    final BlobReader observable = accessor.new BlobReader(context);
    final CountDownLatch errorReceived = new CountDownLatch(1);
    observable.observe(
        observable.getPartitions(),
        Lists.newArrayList(status),
        new BatchLogObserver() {

          @Override
          public boolean onNext(StreamElement element) {
            throw new UnsupportedOperationException("Failure.");
          }

          @Override
          public boolean onError(Throwable error) {
            errorReceived.countDown();
            return false;
          }

          @Override
          public void onCompleted() {
            // Noop.
          }
        });
    assertTrue(errorReceived.await(5, TimeUnit.SECONDS));
  }

  @Test
  public void testObservePartitionsReaderExceptionWithRetry() throws InterruptedException {
    writePartitions(Arrays.asList(0L, 1L, 2L));
    final BlobReader observable = accessor.new BlobReader(context);
    final CountDownLatch errorReceived = new CountDownLatch(10);
    observable.observe(
        observable.getPartitions(),
        Lists.newArrayList(status),
        new BatchLogObserver() {

          @Override
          public boolean onNext(StreamElement element) {
            throw new UnsupportedOperationException("Failure.");
          }

          @Override
          public boolean onError(Throwable error) {
            errorReceived.countDown();
            // Retry until zero.
            return errorReceived.getCount() > 0;
          }

          @Override
          public void onCompleted() {
            // Noop.
          }
        });
    assertTrue(errorReceived.await(5, TimeUnit.SECONDS));
  }

  private void writePartitions(List<Long> stamps) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    BlobWriter writer = accessor.new BlobWriter(context);
    stamps
        .stream()
        .map(
            stamp ->
                StreamElement.upsert(
                    gateway,
                    status,
                    UUID.randomUUID().toString(),
                    "key",
                    status.getName(),
                    stamp,
                    new byte[] {1}))
        .forEach(
            update ->
                writer.write(
                    update,
                    Long.MIN_VALUE,
                    (succ, exc) -> {
                      latch.countDown();
                    }));
    writer.updateWatermark(Long.MAX_VALUE);
    assertTrue(latch.await(5, TimeUnit.SECONDS));
  }
}
