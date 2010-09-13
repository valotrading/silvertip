package silvertip;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public abstract class AbstractMessageParser<T> implements MessageParser<T> {
  @Override final public T parse(ByteBuffer buffer) throws PartialMessageException {
    try {
      byte[] data = onParse(buffer);
      return newMessage(data);
    } catch (BufferUnderflowException e) {
      throw new PartialMessageException();
    } catch (GarbledMessageException e) {
      buffer.reset();
      byte[] data = new byte[buffer.limit() - buffer.position()];
      buffer.get(data);
      return newMessage(data);
    }
  }

  protected abstract byte[] onParse(ByteBuffer buffer) throws GarbledMessageException, PartialMessageException;

  protected abstract T newMessage(byte[] data);
}
