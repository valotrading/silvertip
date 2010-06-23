package silvertip;

import java.nio.ByteBuffer;

public interface MessageParser {
  /**
   * Note: <code>buffer</code> is guaranteed to have mark at the beginning of
   * the next available message. If you call <code>buffer.reset()</code> in your
   * implementation, you must not throw <code>PartialMessageException</code> or
   * <code>GarbledMessageException</code> after that; otherwise the results are
   * undefined and can result in data corruption.
   */
  Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException;
}