package silvertip;

import java.util.Iterator;

public interface Callback {
  void messages(Iterator<Message> messages);
  void timedOut();
}
