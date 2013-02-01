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

public interface EventSource {
  /**
   * Waits for events on the event source.
   *
   * @param timeout Timeout in milliseconds. <code>timeout</code> of zero makes
   *                <code>poll</code> return immediately.
   * @throws IllegalArgumentException if <code>timeout</code> is negative.
   * @return <code>true</code> if there are new events; otherwise returns
   *         <code>false</code>.
   */
  boolean poll(long timeout) throws IOException;

  /*
   * Stops the event source.
   */
  void stop();
}
