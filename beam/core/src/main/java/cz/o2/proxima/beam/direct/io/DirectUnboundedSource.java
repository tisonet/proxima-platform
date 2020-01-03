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
package cz.o2.proxima.beam.direct.io;

import cz.o2.proxima.beam.core.io.StreamElementCoder;
import cz.o2.proxima.direct.commitlog.CommitLogReader;
import cz.o2.proxima.direct.commitlog.LogObserver.OffsetCommitter;
import cz.o2.proxima.direct.commitlog.Offset;
import cz.o2.proxima.direct.core.Partition;
import cz.o2.proxima.repository.RepositoryFactory;
import cz.o2.proxima.storage.StreamElement;
import cz.o2.proxima.storage.commitlog.Position;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.Getter;
import org.apache.beam.repackaged.kryo.com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.apache.beam.sdk.coders.Coder;
import org.apache.beam.sdk.extensions.kryo.KryoCoder;
import org.apache.beam.sdk.io.UnboundedSource;
import org.apache.beam.sdk.options.PipelineOptions;

/** An {@link UnboundedSource} created from direct operator's {@link CommitLogReader}. */
class DirectUnboundedSource
    extends UnboundedSource<StreamElement, DirectUnboundedSource.Checkpoint> {

  static DirectUnboundedSource of(
      RepositoryFactory factory,
      String name,
      CommitLogReader reader,
      Position position,
      boolean eventTime,
      long limit) {

    return new DirectUnboundedSource(factory, name, reader, position, eventTime, limit, null);
  }

  static class Checkpoint implements UnboundedSource.CheckpointMark, Serializable {

    @Getter @Nullable private final Offset offset;
    @Getter private final long limit;
    @Nullable private final transient OffsetCommitter committer;
    @Nullable private final transient OffsetCommitter nackCommitter;
    @Nullable private final transient BeamCommitLogReader reader;

    Checkpoint(BeamCommitLogReader reader) {
      this.offset = reader.getCurrentOffset();
      this.limit = reader.getLimit();
      this.committer = reader.hasExternalizableOffsets() ? null : reader.getLastReadCommitter();
      this.nackCommitter =
          reader.hasExternalizableOffsets() ? null : reader.getLastWrittenCommitter();
      this.reader = reader;
    }

    @Override
    public void finalizeCheckpoint() throws IOException {
      if (committer != null) {
        committer.confirm();
      }
      if (nackCommitter != null) {
        // return any possibly uncommitted, but already read data back to
        // commit log reader
        nackCommitter.nack();
      }
      if (reader != null && !reader.hasExternalizableOffsets()) {
        reader.clearIncomingQueue();
      }
    }

    @Override
    public String toString() {
      return "DirectUnboundedSource.Checkpoint("
          + "offset="
          + offset
          + ", limit="
          + limit
          + ", committer="
          + committer
          + ", nackCommitter="
          + nackCommitter
          + ")";
    }
  }

  private final RepositoryFactory factory;
  private final String name;
  private final CommitLogReader reader;
  private final Position position;
  private final boolean eventTime;
  private final List<Partition> partitions;
  private final long limit;
  private final @Nullable Partition partition;

  DirectUnboundedSource(
      RepositoryFactory factory,
      String name,
      CommitLogReader reader,
      Position position,
      boolean eventTime,
      long limit,
      @Nullable Partition partition) {

    this.factory = factory;
    this.name = name;
    this.reader = reader;
    this.position = position;
    this.eventTime = eventTime;
    this.partitions = reader.getPartitions();
    this.limit = limit;
    this.partition = partition;
  }

  @Override
  public List<UnboundedSource<StreamElement, Checkpoint>> split(
      int desiredNumSplits, PipelineOptions options) throws Exception {

    if (partition != null) {
      return Arrays.asList(this);
    }

    long splittable = partitions.stream().filter(Partition::isSplittable).count();
    long nonSplittable = partitions.size() - splittable;
    int splitDesired =
        splittable > 0 ? Math.max(0, (int) ((desiredNumSplits - nonSplittable) / splittable)) : 0;
    int resulting = (int) (partitions.size() - splittable + splittable * splitDesired);
    return partitions
        .stream()
        .flatMap(
            p ->
                p.isSplittable() && splitDesired > 0
                    ? p.split(splitDesired).stream()
                    : Stream.of(p))
        .map(
            p ->
                new DirectUnboundedSource(
                    factory, name, reader, position, eventTime, limit / resulting, p))
        .collect(Collectors.toList());
  }

  @Override
  public UnboundedReader<StreamElement> createReader(PipelineOptions po, Checkpoint cmt)
      throws IOException {

    Offset offset = cmt == null ? null : cmt.getOffset();
    long readerLimit = cmt == null ? limit : cmt.getLimit();
    return BeamCommitLogReader.unbounded(
        this, name, reader, position, eventTime, readerLimit, partition, offset);
  }

  @Override
  public Coder<Checkpoint> getCheckpointMarkCoder() {
    return KryoCoder.of(kryo -> kryo.addDefaultSerializer(Checkpoint.class, new JavaSerializer()));
  }

  @Override
  public Coder<StreamElement> getOutputCoder() {
    return StreamElementCoder.of(factory);
  }

  @Override
  public boolean requiresDeduping() {
    // when offsets are externalizable we have certainty that we can pause and
    // continue without duplicates
    return !reader.hasExternalizableOffsets() && eventTime;
  }
}
