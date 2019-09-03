/**
 * Copyright 2017-${Year} O2 Czech Republic, a.s.
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

import cz.o2.proxima.annotations.Stable;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.storage.StreamElement;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

/** Dummy storage printing data to stdout. */
@Stable
public class StdoutStorage implements DataAccessorFactory {

  @Override
  public Accept accepts(URI uri) {
    return uri.getScheme().equals("stdout") ? Accept.ACCEPT : Accept.REJECT;
  }

  @Override
  public DataAccessor createAccessor(
      DirectDataOperator op, EntityDescriptor entity, URI uri, Map<String, Object> cfg) {

    return new DataAccessor() {

      @Override
      public Optional<AttributeWriterBase> getWriter(Context context) {
        return Optional.of(
            new AbstractOnlineAttributeWriter(entity, uri) {
              @Override
              public void write(StreamElement data, CommitCallback callback) {
                System.out.println(
                    String.format(
                        "Writing entity %s to attribute %s with key %s and value of size %d",
                        data.getEntityDescriptor(),
                        data.getAttributeDescriptor(),
                        data.getKey(),
                        data.getValue().length));
                callback.commit(true, null);
              }
            });
      }
    };
  }
}
