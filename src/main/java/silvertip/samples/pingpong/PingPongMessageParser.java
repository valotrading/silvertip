package silvertip.samples.pingpong;

import java.nio.ByteBuffer;

import silvertip.Message;
import silvertip.MessageParser;
import silvertip.PartialMessageException;

public class PingPongMessageParser implements MessageParser<Message> {
  /**
   * Returns first complete message found in @buffer. The
   * <code>position()</code> of @buffer points to the beginning of the first
   * partial message (if any) when this method returns.
   *
   * @throws PartialMessageException
   */
  public Message parse(ByteBuffer buffer) throws PartialMessageException {
    for (int pos = buffer.position(); pos < buffer.limit(); pos++) {
      byte b = buffer.get(pos);
      if (b == '\n') {
        byte[] message = new byte[pos - buffer.position() + 1];
        buffer.get(message);
        return new Message(message);
      }
    }
    throw new PartialMessageException();
  }
}