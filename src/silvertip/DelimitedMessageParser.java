package silvertip;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DelimitedMessageParser {
  public List<Message> parse(ByteBuffer buffer) {
    buffer.flip();
    List<Message> result = process(buffer);
    buffer.compact();
    return result;
  }

  /**
   * Returns a list of complete messages that were found in @buffer. The
   * <code>position()</code> of @buffer points to the beginning of the first
   * partial message (if any) when this method returns.
   */
  private List<Message> process(ByteBuffer buffer) {
    List<Message> result = new ArrayList<Message>();
    for (int i = buffer.position(); i < buffer.limit(); i++) {
      byte b = buffer.get(i);
      if (b == '\n') {
        byte[] message = new byte[i - buffer.position() + 1];
        buffer.get(message);
        result.add(new Message(message));
      }
    }
    return result;
  }
}