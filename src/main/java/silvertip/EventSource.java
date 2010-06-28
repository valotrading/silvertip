package silvertip;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

public interface EventSource {
  void read(SelectionKey key) throws IOException;

  SelectableChannel getChannel();

  void timeout();

  void setSelectionKey(SelectionKey selectionKey);
}
