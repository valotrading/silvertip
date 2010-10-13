package silvertip;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public interface EventSource {
  SelectionKey register(Selector selector, int ops) throws IOException;

  void unregister();

  void read(SelectionKey key) throws IOException;

  EventSource accept(SelectionKey key) throws IOException;

  void timeout();

  boolean isClosed();
}
