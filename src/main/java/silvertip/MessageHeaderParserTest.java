package silvertip;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class MessageHeaderParserTest {
  private static final int RECEIVE_BUFFER_SIZE = 16;

  private final ByteBuffer receiveBuffer = ByteBuffer.allocate(RECEIVE_BUFFER_SIZE);
  private final MessageHeaderParser parser = new MessageHeaderParser();

  @Test
  public void partialHeader() {
    receiveBuffer.put("L=3".getBytes());
    List<Message> messages = parser.parse(receiveBuffer);
    Assert.assertEquals(0, messages.size());
  }

  @Test
  public void partialHeaderFilled() {
    List<Message> messages;

    receiveBuffer.put("L=3".getBytes());
    messages = parser.parse(receiveBuffer);
    Assert.assertEquals(0, messages.size());

    receiveBuffer.put("|FOO".getBytes());
    messages = parser.parse(receiveBuffer);
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("FOO", messages.get(0).toString());
  }

  @Test
  public void partialPayload() {
    receiveBuffer.put("L=3|FO".getBytes());
    List<Message> messages = parser.parse(receiveBuffer);
    Assert.assertEquals(0, messages.size());
  }

  @Test
  public void partialPayloadFilled() {
    List<Message> messages;

    receiveBuffer.put("L=3|FO".getBytes());
    messages = parser.parse(receiveBuffer);
    Assert.assertEquals(0, messages.size());

    receiveBuffer.put("O".getBytes());
    messages = parser.parse(receiveBuffer);
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("FOO", messages.get(0).toString());
  }

  @Test
  public void multipleMessages() {
    receiveBuffer.put("L=3|FOOL=3|BAR".getBytes());
    MessageHeaderParser parser = new MessageHeaderParser();
    List<Message> messages = parser.parse(receiveBuffer);
    Assert.assertEquals(2, messages.size());
    Assert.assertEquals("FOO", messages.get(0).toString());
    Assert.assertEquals("BAR", messages.get(1).toString());
  }
}