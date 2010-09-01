package silvertip;

import java.nio.ByteBuffer;
import java.util.Arrays;

import junit.framework.Assert;

import org.junit.Test;

public class AbstractMessageParserTest extends AbstractMessageParser<Message> {

  @Test public void parsingValidData() throws PartialMessageException {
    byte[] data = "FOO".getBytes();
    AbstractMessageParser<Message> parser = new AbstractMessageParserTest();
    ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOf(data, data.length));

    Message result = parser.parse(buffer);

    assertArraysEquals(data, result.payload());
  }

  @Test(expected = PartialMessageException.class) public void partialMessage() throws PartialMessageException {
    AbstractMessageParser<Message> parser = new AbstractMessageParserTest() {
      @Override protected byte[] onParse(ByteBuffer buffer) throws GarbledMessageException,
          PartialMessageException {
        throw new PartialMessageException();
      }
    };
    parser.parse(ByteBuffer.allocate(0));
  }

  @Test public void garbledMessage() throws PartialMessageException {
    final String garbled1 = "FOO";
    final String garbled2 = "BAR";
    ByteBuffer buffer = ByteBuffer.wrap((garbled1 + garbled2).getBytes());
    AbstractMessageParser<Message> parser = new AbstractMessageParserTest() {
      @Override protected byte[] onParse(ByteBuffer buffer) throws GarbledMessageException,
          PartialMessageException {
        for (int i = 0; i < garbled1.length(); i++)
          buffer.get();
        throw new GarbledMessageException();
      }
    };
    checkNextMessageData(garbled1+garbled2, buffer, parser);
  }

  private void checkNextMessageData(String original, ByteBuffer buffer, AbstractMessageParser<Message> parser)
      throws PartialMessageException {
    buffer.mark();
    Message message = parser.parse(buffer);
    assertArraysEquals(original.getBytes(), message.payload());
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
