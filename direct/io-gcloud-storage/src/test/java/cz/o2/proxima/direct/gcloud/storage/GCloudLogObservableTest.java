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

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.StorageException;
import com.typesafe.config.ConfigFactory;
import cz.o2.proxima.direct.blob.BlobLogObservable.ThrowingRunnable;
import cz.o2.proxima.direct.core.DirectDataOperator;
import cz.o2.proxima.direct.gcloud.storage.GCloudBlobPath.GCloudBlob;
import cz.o2.proxima.repository.EntityDescriptor;
import cz.o2.proxima.repository.Repository;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.Test;

public class GCloudLogObservableTest {

  final Repository repo = Repository.ofTest(ConfigFactory.load("test-reference.conf").resolve());
  final DirectDataOperator direct = repo.getOrCreateOperator(DirectDataOperator.class);
  final EntityDescriptor gateway = repo.getEntity("gateway");
  final GCloudStorageAccessor accessor =
      new GCloudStorageAccessor(gateway, URI.create("gs://bucket/path"), cfg());

  private Map<String, Object> cfg() {
    return new HashMap<String, Object>() {
      {
        put("initial-retry-delay-ms", 10);
        put("max-retry-delay-ms", 50);
      }
    };
  }

  final GCloudLogObservable observable = new GCloudLogObservable(accessor, direct.getContext());

  @Test
  public void testRetries() {
    GCloudBlob blob = new GCloudBlob(mock(Blob.class));
    observable.runHandlingErrors(blob, () -> {});
    observable.runHandlingErrors(blob, throwTimes(() -> jsonResponse(404), 1));
    try {
      observable.runHandlingErrors(blob, throwTimes(() -> jsonResponse(429), 20));
      fail("Should have thrown exception");
    } catch (StorageException ex) {
      // pass
    }
    try {
      observable.runHandlingErrors(blob, throwTimes(() -> new RuntimeException("fail"), 1));
      fail("Should have thrown exception");
    } catch (RuntimeException ex) {
      // pass
    }
  }

  private GoogleJsonResponseException jsonResponse(int code) {
    return new GoogleJsonResponseException(
        new HttpResponseException.Builder(code, "", new HttpHeaders()), new GoogleJsonError());
  }

  private ThrowingRunnable throwTimes(Supplier<Exception> throwSupplier, int times) {
    AtomicInteger counter = new AtomicInteger();
    return () -> {
      if (counter.getAndIncrement() < times) {
        throw throwSupplier.get();
      }
    };
  }
}
