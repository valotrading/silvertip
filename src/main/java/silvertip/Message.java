package silvertip;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Message {
  private static final Charset US_ASCII = Charset.forName("US-ASCII");

  private final byte[] payload;

  public static Message fromString(String s) {
    return new Message(s.getBytes(US_ASCII));
  }

  public Message(byte[] payload) {
    this.payload = payload;
  }

  public byte[] payload() {
    return payload;
  }

  public ByteBuffer toByteBuffer() {
    return ByteBuffer.wrap(payload());
  }

  @Override
  public String toString() {
    return new String(payload, US_ASCII);
  }
}
