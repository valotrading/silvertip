package silvertip;

import java.nio.ByteBuffer;

public interface MessageParser<T extends Message> {
  /**
   * Parses one message from <code>buffer</code> and returns it.
   * <p>
   * <b>Do's & Don'ts for implementors</b>:
   * 
   * <ul>
   * <li>If you return a <code>Message</code> from the <code>parse</code>
   * method, make sure <code>buffer</code> position points to the beginning of
   * the next full or partial message, or at the limit of <code>buffer</code>.</li>
   * <li>If you throw <code>PartialMessageException</code> or
   * <code>GarbledMessageException</code>, you don't need to restore
   * <code>buffer</code> position yourself -- callers of
   * <code>MessageParser.parse()</code> are expected to do that for you. For
   * that to work, however, you must never call <code>buffer.mark()</code> in
   * your implementation; otherwise you will lose data.</li>
   * <li><code>buffer</code> is guaranteed to have mark at the beginning of the
   * first full or partial message in <code>buffer</code>. You are allowed to
   * call <code>buffer.reset()</code> in your <code>MessageParser.parse()</code>
   * implementation but you must not throw <code>PartialMessageException</code>
   * or <code>GarbledMessageException</code> after that; otherwise you will lose
   * data.</li>
   * </ul>
   * 
   * @throws PartialMessageException
   *           if <code>buffer</code> does not have one full message in it.
   * @throws GarbledMessageException
   *           if the first message in <code>buffer</code> does not follow
   *           expected message format.
   */
  T parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException;
}
