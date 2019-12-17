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
package cz.o2.proxima.server;

import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Utils {

  static void die(String message) {
    die(message, null);
  }

  static void die(String message, @Nullable Throwable error) {
    try {
      // sleep random time between zero to 10 seconds to break ties
      Thread.sleep((long) (Math.random() * 10000));
    } catch (InterruptedException ex) {
      // just for making sonar happy :-)
      Thread.currentThread().interrupt();
    }
    if (error == null) {
      log.error(message);
    } else {
      log.error(message, error);
    }
    System.exit(1);
  }

  private Utils() {
    // do not construct
  }
}