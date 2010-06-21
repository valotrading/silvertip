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
      byte[] payload = new byte[header.getLength()];
      buffer.get(payload);
      String trailer = trailer(buffer);
      return Message.fromString(header.toString() + new String(payload) + trailer);
    } catch (BufferUnderflowException e) {
      throw new PartialMessageException();
    }
  }

  private FixMessageHeader header(ByteBuffer buffer) throws GarbledMessageException {
    match(buffer, "8=");
    String beginString = value(buffer);
    match(buffer, "9=");
    String length = value(buffer);

    StringBuilder header = new StringBuilder();
    header.append("8=");
    header.append(beginString);
    header.append(DELIMITER);
    header.append("9=");
    header.append(length);
    header.append(DELIMITER);
    return new FixMessageHeader(header.toString(), Integer.parseInt(length));
  }

  private String trailer(ByteBuffer buffer) throws GarbledMessageException {
    match(buffer, "10=");
    String checksum = value(buffer);

    StringBuilder result = new StringBuilder();
    result.append("10=");
    result.append(checksum);
    result.append(DELIMITER);
    return result.toString();
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
    private final String header;
    private final int lenght;

    public FixMessageHeader(String header, int length) {
      this.header = header;
      this.lenght = length;
    }

    public int getLength() {
      return lenght;
    }

    @Override
    public String toString() {
      return header;
    }
  };
}
