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
package cz.o2.proxima.direct.gcloud.storage;

import static org.junit.Assert.*;

import com.typesafe.config.ConfigFactory;
import cz.o2.proxima.direct.bulk.NamingConvention;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.repository.Repository;
import java.net.URI;
import java.util.Collections;
import org.junit.Test;

public class GCloudStorageAccessorTest {

  private final Repository repo =
      Repository.of(ConfigFactory.load("test-reference.conf").resolve());
  private final EntityDescriptor entity = repo.getEntity("gateway");

  @Test
  public void testNamingConventionWithBucket() {
    GCloudStorageAccessor accessor =
        new GCloudStorageAccessor(entity, URI.create("gs://bucket/path"), Collections.emptyMap());
    NamingConvention convention = accessor.getNamingConvention();
    assertTrue(convention.nameOf(1500000000000L).startsWith("/2017/07/"));
  }

  @Test
  public void testNamingConventionWithBucketAndNoPath() {
    GCloudStorageAccessor accessor =
        new GCloudStorageAccessor(entity, URI.create("gs://bucket"), Collections.emptyMap());
    NamingConvention convention = accessor.getNamingConvention();
    assertTrue(convention.nameOf(1500000000000L).startsWith("/2017/07/"));
  }
}
