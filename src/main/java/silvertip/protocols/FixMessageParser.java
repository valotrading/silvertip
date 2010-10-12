package silvertip.protocols;

import java.nio.ByteBuffer;

import silvertip.AbstractMessageParser;
import silvertip.GarbledMessageException;
import silvertip.Message;
import silvertip.PartialMessageException;

public class FixMessageParser extends AbstractMessageParser<Message> {
  public static final char DELIMITER = '\001';

  @Override protected byte[] onParse(ByteBuffer buffer) throws GarbledMessageException, PartialMessageException {
    FixMessageHeader header = header(buffer);
    int start = buffer.position();
    int trailerStart = start + header.getBodyLength();
    if (trailerStart > buffer.limit()) {
      throw new PartialMessageException();
    }
    buffer.position(trailerStart);
    int trailerLength = trailer(buffer);
    byte[] message = new byte[header.getHeaderLength() + header.getBodyLength() + trailerLength];
    buffer.reset();
    buffer.get(message);
    return message;
  }

  @Override protected Message newMessage(byte[] data) {
    return new Message(data);
  }

  private FixMessageHeader header(ByteBuffer buffer) throws GarbledMessageException {
    int start = buffer.position();
    match(buffer, "8=");
    value(buffer);
    int bodyLength = bodyLength(buffer);
    int headerLength = buffer.position() - start;
    match(buffer, "35=");
    value(buffer);
    return new FixMessageHeader(headerLength, bodyLength);
  }

  private int bodyLength(ByteBuffer buffer) throws GarbledMessageException {
    try {
      match(buffer, "9=");
      return Integer.parseInt(value(buffer));
    } catch (NumberFormatException e) {
      throw new GarbledMessageException();
    }
  }

  private int trailer(ByteBuffer buffer) throws GarbledMessageException {
    int start = buffer.position();
    try {
      match(buffer, "10=");
      value(buffer);
    } catch (GarbledMessageException e) {
      buffer.position(start);
      throw e;
    }
    return buffer.position() - start;
  }

  private void match(ByteBuffer buffer, String s) throws GarbledMessageException {
    for (int i = 0; i < s.length(); i++) {
      int c = buffer.get();
      if (c != s.charAt(i)) {
        throw new GarbledMessageException();
      }
    }
  }

  private String value(ByteBuffer buffer) throws GarbledMessageException {
    StringBuilder result = new StringBuilder();
    for (;;) {
      byte ch = buffer.get();
      if (ch == DELIMITER)
        break;
      result.append((char) ch);
    }
    if (result.length() == 0)
      throw new GarbledMessageException();
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
  }
}
