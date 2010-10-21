package silvertip.protocols;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import silvertip.AbstractMessageParser;
import silvertip.GarbledMessageException;
import silvertip.Message;
import silvertip.PartialMessageException;

public class FixMessageParser extends AbstractMessageParser<Message> {
  public static final char DELIMITER = '\001';

  @Override protected byte[] onParse(ByteBuffer buffer) throws GarbledMessageException, PartialMessageException {
    try {
      FixMessageHeader header = header(buffer);
      int trailerLength = trailer(buffer, header);
      byte[] message = new byte[header.getHeaderLength() + header.getBodyLength() + trailerLength];
      buffer.reset();
      buffer.get(message);
      return message;
    } catch (GarbledMessageException e) {
      int nextMessagePosition = nextMessagePosition(buffer);
      buffer.reset();
      byte[] data = new byte[nextMessagePosition - buffer.position()];
      buffer.get(data);
      e.setMessageData(data);
      throw e;
    }
  }

  @Override protected Message newMessage(byte[] data) {
    return new Message(data);
  }

  private FixMessageHeader header(ByteBuffer buffer) throws GarbledMessageException {
    int start = buffer.position();
    String beginString = parseField(buffer, Tag.BEGIN_STRING);
    if (!isBeginStringValid(beginString))
      throw new GarbledMessageException(Tag.BEGIN_STRING + " is invalid, expected format FIX.m.n: " + beginString);
    String bodyLength = parseField(buffer, Tag.BODY_LENGTH);
    return new FixMessageHeader(buffer.position() - start, Integer.parseInt(bodyLength), buffer.position());
  }

  private boolean isBeginStringValid(String beginString) {
    return beginString.startsWith("FIX.") && Character.isDigit(beginString.charAt(4)) &&
      Character.isDigit(beginString.charAt(6));
  }

  private int trailer(ByteBuffer buffer, FixMessageHeader header) throws GarbledMessageException, PartialMessageException {
    int trailerStart = header.getBodyStart() + header.getBodyLength();
    if (trailerStart > buffer.limit()) {
      throw new PartialMessageException();
    }
    buffer.position(trailerStart);
    try {
      parseField(buffer, Tag.CHECKSUM);
    } catch (GarbledMessageException e) {
      buffer.position(header.getBodyStart());
      throw e;
    }
    return buffer.position() - trailerStart;
  }

  private String parseField(ByteBuffer buffer, Tag tag) throws GarbledMessageException {
    match(buffer, tag);
    return value(buffer, tag);
  }

  private void match(ByteBuffer buffer, Tag tag) throws GarbledMessageException {
    String expected = tag.number() + "=";
    for (int i = 0; i < expected.length(); i++) {
      int c = buffer.get();
      if (c != expected.charAt(i)) {
        throw new GarbledMessageException();
      }
    }
  }

  private String value(ByteBuffer buffer, Tag tag) throws GarbledMessageException {
    StringBuilder result = new StringBuilder();
    for (;;) {
      byte ch = buffer.get();
      if (ch == DELIMITER)
        break;
      result.append((char) ch);
    }
    if (result.length() == 0)
      throw new GarbledMessageException(tag + " is empty");
    return result.toString();
  }

  private int nextMessagePosition(ByteBuffer buffer) {
    /* Store the original position to which the buffer is finally rewound. */
    int start = buffer.position();
    try {
      while (buffer.hasRemaining()) {
        if (skipToBeginString(buffer))
          return buffer.position();
      }
      return buffer.position();
    } catch (BufferUnderflowException e) {
      return buffer.position();
    } finally {
      buffer.position(start);
    }
  }

  private boolean skipToBeginString(ByteBuffer buffer) {
    String beginString = DELIMITER + "8=";
    for (int i = 0; i < beginString.length(); i++) {
      if (buffer.get() != beginString.charAt(i))
        return false;
    }
    /* Set the buffer position to the beginning of BeginString(8) */
    buffer.position(buffer.position() - beginString.length() + 1);
    return true;
  }

  private static class FixMessageHeader {
    private final int headerLength;
    private final int bodyLength;
    private final int bodyStart;

    public FixMessageHeader(int headerLength, int bodyLength, int bodyStart) {
      this.headerLength = headerLength;
      this.bodyLength = bodyLength;
      this.bodyStart = bodyStart;
    }

    public int getHeaderLength() {
      return headerLength;
    }

    public int getBodyLength() {
      return bodyLength;
    }

    public int getBodyStart() {
      return bodyStart;
    }
  }

  private enum Tag {
    BEGIN_STRING(8, "BeginString"),
    BODY_LENGTH(9, "BodyLength"),
    MSG_TYPE(35, "MsgType"),
    CHECKSUM(10, "CheckSum");

    private int tagNumber;
    private String name;

    private Tag(int tagNumber, String name) {
      this.tagNumber = tagNumber;
      this.name = name;
    }

    public int number() {
      return tagNumber;
    }

    public String toString() {
      return String.format("%s(%d)", name, tagNumber);
    }
  }
}
