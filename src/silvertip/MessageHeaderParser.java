package silvertip;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MessageHeaderParser {
  public List<Message> parse(ByteBuffer buffer) {
    buffer.flip();
    List<Message> result = process(buffer);
    buffer.compact();
    return result;
  }

  private List<Message> process(ByteBuffer buffer) {
    List<Message> result = new ArrayList<Message>();
    while (buffer.hasRemaining()) {
      int length;
      try {
        buffer.mark();
        length = header(buffer);
      } catch (BufferUnderflowException e) {
        buffer.reset();
        break;
      }
      if (length < 0)
        continue;
      try {
        result.add(payload(buffer, length));
      } catch (BufferUnderflowException e) {
        buffer.reset();
        break;
      }
    }
    return result;
  }

  private Message payload(ByteBuffer buffer, int length) {
    byte[] payload = new byte[length];
    buffer.get(payload);
    return new Message(payload);
  }

  private int header(ByteBuffer buffer) {
    if (!match(buffer, 'L'))
      return -1;
    if (!match(buffer, '='))
      return -1;
    int len = Integer.parseInt(Character.toString((char) buffer.get()));
    if (!match(buffer, '|'))
      return -1;
    return len;
  }

  private boolean match(ByteBuffer buffer, char c) {
    return buffer.get() == c;
  }
}
