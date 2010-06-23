package silvertip.protocols;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import silvertip.GarbledMessageException;
import silvertip.Message;
import silvertip.MessageParser;
import silvertip.PartialMessageException;

public class FixMessageParser implements MessageParser {
  public static final char DELIMITER = '\001';

  @Override
  public Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException {
    try {
      FixMessageHeader header = header(buffer);
      buffer.position(buffer.position() + header.getBodyLength());
      int trailerLength = trailer(buffer);
      byte[] message = new byte[header.getHeaderLength() + header.getBodyLength() + trailerLength];
      buffer.reset();
      buffer.get(message);
      return new Message(message);
    } catch (BufferUnderflowException e) {
      throw new PartialMessageException();
    }
  }

  private FixMessageHeader header(ByteBuffer buffer) throws GarbledMessageException {
    int start = buffer.position();
    match(buffer, "8=");
    value(buffer);
    match(buffer, "9=");
    String bodyLength = value(buffer);

    return new FixMessageHeader(buffer.position() - start, Integer.parseInt(bodyLength));
  }

  private int trailer(ByteBuffer buffer) throws GarbledMessageException {
    int start = buffer.position();
    match(buffer, "10=");
    value(buffer);
    return buffer.position() - start;
  }

  private void match(ByteBuffer buffer, String s) throws GarbledMessageException {
    for (int i = 0; i < s.length(); i++) {
      if (buffer.get() != s.charAt(i))
        throw new GarbledMessageException();
    }
  }

  private String value(ByteBuffer buffer) {
    StringBuilder result = new StringBuilder();
    for (;;) {
      byte ch = buffer.get();
      if (ch == DELIMITER)
        break;
      result.append((char) ch);
    }
    return result.toString();
  }

  private static class FixMessageHeader {
    private final int headerLength;
    private final int bodyLength;

    public FixMessageHeader(int headerLength, int bodyLength) {
      this.headerLength = headerLength;
      this.bodyLength = bodyLength;
    }

    public int getHeaderLength() {
      return headerLength;
    }

    public int getBodyLength() {
      return bodyLength;
    }
  };
}
