package silvertip;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class DelimitedReceiveBuffer {
  public List<String> parse(ByteBuffer buffer) {
    buffer.flip();
    List<String> result = process(buffer);
    buffer.compact();
    return result;
  }

  /**
   * Returns a list of complete messages that were found in @buffer. The
   * <code>position()</code> of @buffer points to the beginning of the first
   * partial message (if any) when this method returns.
   */
  private List<String> process(ByteBuffer buffer) {
    List<String> result = new ArrayList<String>();
    for (int i = buffer.position(); i < buffer.limit(); i++) {
      byte b = buffer.get(i);
      if (b == '\n') {
        byte[] message = new byte[i - buffer.position() + 1];
        buffer.get(message);
        result.add(new String(message));
      }
    }
    return result;
  }
}