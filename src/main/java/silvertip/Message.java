package silvertip;

import java.nio.ByteBuffer;

public class Message {
  private final byte[] payload;

  public static Message fromString(String s) {
    return new Message(s.getBytes());
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
    return new String(payload);
  }
}