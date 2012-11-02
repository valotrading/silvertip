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
package silvertip;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class AbstractMessageParserTest extends AbstractMessageParser<Message> {

  @Test
  public void parsingValidData() throws GarbledMessageException, PartialMessageException {
    byte[] data = "FOO".getBytes();
    AbstractMessageParser<Message> parser = new AbstractMessageParserTest();
    ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOf(data, data.length));

    Message result = parser.parse(buffer);

    assertArraysEquals(data, result.payload());
  }

  @Test(expected = PartialMessageException.class)
  public void partialMessage() throws GarbledMessageException, PartialMessageException {
    AbstractMessageParser<Message> parser = new AbstractMessageParserTest() {
      @Override protected byte[] onParse(ByteBuffer buffer) throws GarbledMessageException,
          PartialMessageException {
        throw new PartialMessageException();
      }
    };
    parser.parse(ByteBuffer.allocate(0));
  }

  @Override protected Message newMessage(byte[] data) {
    return new Message(data);
  }

  @Override protected byte[] onParse(ByteBuffer buffer) throws GarbledMessageException, PartialMessageException {
    return buffer.array();
  }

  private static void assertArraysEquals(byte[] a1, byte[] a2) {
    Assert.assertTrue(Arrays.equals(a1, a2));
  }
}
