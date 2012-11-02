/*
 * Copyright 2012 the original author or authors.
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
package silvertip.samples.pingpong;

import java.nio.ByteBuffer;

import silvertip.MessageParser;
import silvertip.PartialMessageException;

public class PingPongMessageParser implements MessageParser<String> {
  /**
   * Returns first complete message found in @buffer. The
   * <code>position()</code> of @buffer points to the beginning of the first
   * partial message (if any) when this method returns.
   *
   * @throws PartialMessageException
   */
  public String parse(ByteBuffer buffer) throws PartialMessageException {
    for (int pos = buffer.position(); pos < buffer.limit(); pos++) {
      byte b = buffer.get(pos);
      if (b == '\n') {
        byte[] message = new byte[pos - buffer.position() + 1];
        buffer.get(message);
        return new String(message);
      }
    }
    throw new PartialMessageException();
  }
}
