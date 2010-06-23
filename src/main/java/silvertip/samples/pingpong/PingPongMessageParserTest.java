package silvertip.samples.pingpong;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import silvertip.Message;
import silvertip.PartialMessageException;

public class PingPongMessageParserTest {
  private static final int RECEIVE_BUFFER_SIZE = 16;

  private final ByteBuffer receiveBuffer = ByteBuffer.allocate(RECEIVE_BUFFER_SIZE);
  private final PingPongMessageParser protocol = new PingPongMessageParser();

  @Test(expected = PartialMessageException.class)
  public void empty() throws Exception {
    parse();
  }

  @Test(expected = PartialMessageException.class)
  public void partialMessage() throws Exception {
    receiveBuffer.put("PO".getBytes());
    receiveBuffer.flip();
    parse();
  }

  @Test
  public void completeMessage() throws Exception {
    receiveBuffer.put("PONG\n".getBytes());
    receiveBuffer.flip();
    Message message = parse();
    Assert.assertEquals("PONG\n", message.toString());
  }

  private Message parse() throws PartialMessageException {
    return protocol.parse(receiveBuffer);
  }
}