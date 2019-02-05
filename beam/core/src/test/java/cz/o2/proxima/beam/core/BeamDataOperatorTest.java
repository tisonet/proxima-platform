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
package cz.o2.proxima.beam.core;

import com.typesafe.config.ConfigFactory;
import cz.o2.proxima.direct.core.DirectDataOperator;
import cz.o2.proxima.direct.core.OnlineAttributeWriter;
import cz.o2.proxima.repository.AttributeDescriptor;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.repository.Repository;
import cz.o2.proxima.storage.StreamElement;
import cz.o2.proxima.storage.commitlog.Position;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.extensions.euphoria.core.client.operator.CountByKey;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/**
 * Test suite for {@link BeamDataOperator}.
 */
public class BeamDataOperatorTest {

  final Repository repo = Repository.of(ConfigFactory.load("test-reference.conf"));
  final BeamDataOperator beam = repo.asDataOperator(BeamDataOperator.class);
  final DirectDataOperator direct = beam.getDirect();
  final EntityDescriptor gateway = repo.findEntity("gateway")
      .orElseThrow(() -> new IllegalStateException("Missing entity gateway"));
  final AttributeDescriptor<?> armed = gateway.findAttribute("armed")
      .orElseThrow(() -> new IllegalStateException("Missing attribute armed"));
  final long now = System.currentTimeMillis();

  Pipeline pipeline;

  @Before
  public void setUp() {
    pipeline = Pipeline.create();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testBoundedCommitLogConsumption() {
    direct.getWriter(armed)
        .orElseThrow(() -> new IllegalStateException("Missing writer for armed"))
        .write(StreamElement.update(gateway, armed, "uuid", "key", armed.getName(),
            now, new byte[] { 1, 2, 3}), (succ, exc) -> { });
    PCollection<StreamElement> stream = beam.getStream(
        pipeline, Position.OLDEST, true, true, armed);
    PCollection<KV<String, Long>> counted = CountByKey.of(stream)
        .keyBy(e -> "", TypeDescriptors.strings())
        .output();
    PAssert.that(counted).containsInAnyOrder(KV.of("", 1L));
    pipeline.run();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testBoundedCommitLogConsumptionWithWindow() {
    OnlineAttributeWriter writer = direct.getWriter(armed)
        .orElseThrow(() -> new IllegalStateException("Missing writer for armed"));

    writer.write(StreamElement.update(
        gateway, armed, "uuid", "key1", armed.getName(),
            now - 5000, new byte[] { 1, 2, 3}), (succ, exc) -> { });
    writer.write(StreamElement.update(
        gateway, armed, "uuid", "key2", armed.getName(),
            now, new byte[] { 1, 2, 3}), (succ, exc) -> { });

    PCollection<StreamElement> stream = beam.getStream(
        pipeline, Position.OLDEST, true, true, armed);

    PCollection<KV<String, Long>> counted = CountByKey.of(stream)
        .keyBy(e -> "", TypeDescriptors.strings())
        .windowBy(FixedWindows.of(Duration.millis(1000)))
        .triggeredBy(AfterWatermark.pastEndOfWindow())
        .discardingFiredPanes()
        .withAllowedLateness(Duration.ZERO)
        .output();

    PAssert.that(counted).containsInAnyOrder(KV.of("", 1L), KV.of("", 1L));
    pipeline.run();

  }

  @SuppressWarnings("unchecked")
  @Test
  public void testUnboundedCommitLogConsumptionWithWindow() {
    OnlineAttributeWriter writer = direct.getWriter(armed)
        .orElseThrow(() -> new IllegalStateException("Missing writer for armed"));

    writer.write(StreamElement.update(
        gateway, armed, "uuid", "key1", armed.getName(),
            now - 5000, new byte[] { 1, 2, 3}), (succ, exc) -> { });
    writer.write(StreamElement.update(
        gateway, armed, "uuid", "key2", armed.getName(),
            now, new byte[] { 1, 2, 3}), (succ, exc) -> { });

    PCollection<StreamElement> stream = beam.getStream(
        pipeline, Position.OLDEST, false, true, 2, armed);

    PCollection<KV<String, Long>> counted = CountByKey.of(stream)
        .keyBy(e -> "", TypeDescriptors.strings())
        .windowBy(FixedWindows.of(Duration.millis(1000)))
        .triggeredBy(AfterWatermark.pastEndOfWindow())
        .discardingFiredPanes()
        .withAllowedLateness(Duration.ZERO)
        .output();

    PAssert.that(counted).containsInAnyOrder(KV.of("", 1L), KV.of("", 1L));
    pipeline.run();

  }



}
