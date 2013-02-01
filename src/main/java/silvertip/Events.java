/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silvertip;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Events {
  private List<EventSource> sources = new ArrayList<EventSource>();

  public static Events open() throws IOException {
    return new Events(Collections.<EventSource>emptyList());
  }

  public Events(List<EventSource> sources) {
    this.sources = sources;
  }

  public void add(EventSource source) {
    sources.add(source);
  }

  public boolean process(long timeout) throws IOException {
    if (timeout < 0)
      throw new IllegalArgumentException();

    if (timeout == 0) {
      int count = 0;

      for (EventSource source : sources) {
        count++;
        if (source.poll(0)) {
          Collections.rotate(sources, -count);
          return true;
        }
      }
    } else {
      int count = 0;

      for (EventSource source : sources) {
        count++;

        long start = System.nanoTime();

        if (source.poll(timeout)) {
          Collections.rotate(sources, -count);
          return true;
        }

        long end = System.nanoTime();
    
        timeout -= TimeUnit.NANOSECONDS.toMillis(end - start);
        if (timeout <= 0) {
          Collections.rotate(sources, -count);
          return false;
        }
      }
    }

    return false;
  }

  public void stop() {
    for (EventSource source : sources) {
      source.stop();
    }
  }
}
