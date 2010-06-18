package silvertip;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class DelimitedMessageParserTest {
  private static final int RECEIVE_BUFFER_SIZE = 16;

  private final ByteBuffer receiveBuffer = ByteBuffer.allocate(RECEIVE_BUFFER_SIZE);
  private final DelimitedMessageParser protocol = new DelimitedMessageParser();

  @Test
  public void empty() throws Exception {
    List<Message> messages = parse();
    Assert.assertEquals(0, messages.size());
  }

  @Test
  public void multipleMessages() throws Exception {
    receiveBuffer.put("PING\nPONG\n".getBytes());
    List<Message> messages = parse();
    Assert.assertEquals(2, messages.size());
    Assert.assertEquals("PING\n", messages.get(0).toString());
    Assert.assertEquals("PONG\n", messages.get(1).toString());
  }

  @Test
  public void partialMessage() throws Exception {
    receiveBuffer.put("PING\nPO".getBytes());
    List<Message> messages = parse();
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("PING\n", messages.get(0).toString());
  }

  @Test
  public void partialMessageFilled() throws Exception {
    receiveBuffer.put("PING\nPO".getBytes());

    List<Message> messages;

    messages = parse();
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("PING\n", messages.get(0).toString());

    receiveBuffer.put("NG\n".getBytes());
    messages = parse();
    Assert.assertEquals(1, messages.size());
    Assert.assertEquals("PONG\n", messages.get(0).toString());
  }

  @Test
  public void moreMessagesThanFitInReceiveBufferAtOnce() throws Exception {
    String message = "PING\n";
    for (int i = 0; i < RECEIVE_BUFFER_SIZE / message.length() * 2; i++) {
      receiveBuffer.put(message.getBytes());
      List<Message> messages = parse();
      Assert.assertEquals(1, messages.size());
      Assert.assertEquals(message, messages.get(0).toString());
    }
  }

  private List<Message> parse() {
    return protocol.parse(receiveBuffer);
  }
}