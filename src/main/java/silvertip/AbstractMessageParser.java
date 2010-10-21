package silvertip;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public abstract class AbstractMessageParser<T> implements MessageParser<T> {
  @Override final public T parse(ByteBuffer buffer) throws GarbledMessageException, PartialMessageException {
    try {
      byte[] data = onParse(buffer);
      return newMessage(data);
    } catch (BufferUnderflowException e) {
      throw new PartialMessageException();
    }
  }

  protected abstract byte[] onParse(ByteBuffer buffer) throws GarbledMessageException, PartialMessageException;

  protected abstract T newMessage(byte[] data);
}
