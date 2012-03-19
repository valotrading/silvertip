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
