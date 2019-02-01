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
package cz.o2.proxima.beam.direct.io;

import cz.o2.proxima.beam.core.DataAccessor;
import cz.o2.proxima.direct.core.Context;
import cz.o2.proxima.storage.StreamElement;
import cz.o2.proxima.storage.commitlog.Position;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.Read;
import org.apache.beam.sdk.values.PCollection;

/**
 * Wrapper of direct data accessor to beam one.
 */
public class DirectDataAccessorWrapper implements DataAccessor {

  private final cz.o2.proxima.direct.core.DataAccessor direct;
  private final Context context;

  public DirectDataAccessorWrapper(
      cz.o2.proxima.direct.core.DataAccessor direct,
      Context context) {

    this.direct = direct;
    this.context = context;
  }

  @Override
  public PCollection<StreamElement> getCommitLog(
      Pipeline pipeline, Position position, boolean stopAtCurrent,
      boolean eventTime) {

    return pipeline.apply(Read.from(
        DirectUnboundedSource.of(
            direct.getCommitLogReader(context)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Cannot create commit log from " + direct)))));
  }

}
