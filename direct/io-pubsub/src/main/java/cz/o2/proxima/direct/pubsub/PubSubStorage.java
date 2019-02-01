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
package cz.o2.proxima.direct.pubsub;

import cz.o2.proxima.annotations.Stable;
import cz.o2.proxima.direct.core.DataAccessor;
import cz.o2.proxima.direct.core.DataAccessorFactory;
import cz.o2.proxima.direct.core.DirectDataOperator;
import cz.o2.proxima.repository.EntityDescriptor;
import java.net.URI;
import java.util.Map;

/**
 * A {@link DataAccessorFactory} for PubSub.
 */
@Stable
public class PubSubStorage implements DataAccessorFactory {

  @Override
  public DataAccessor createAccessor(
      DirectDataOperator direct,
      EntityDescriptor entityDesc,
      URI uri,
      Map<String, Object> cfg) {

    return new PubSubAccessor(entityDesc, uri, cfg);
  }

  @Override
  public Accept accepts(URI uri) {
    return uri.getScheme().equals("gps") ? Accept.ACCEPT : Accept.REJECT;
  }

}
