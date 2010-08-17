package silvertip;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public interface EventSource {
  SelectionKey register(Selector selector, int ops) throws IOException;

  void read(SelectionKey key) throws IOException;
  void write(SelectionKey key) throws IOException;

  void timeout();

  boolean isClosed();
}
