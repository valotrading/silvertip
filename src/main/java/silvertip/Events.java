package silvertip;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Events {
  private List<EventSource> sources = new ArrayList<EventSource>();
  private Selector selector;
  private long idleMsec;

  public static Events open(long idleMsec) throws IOException {
    return new Events(Selector.open(), idleMsec);
  }

  public Events(Selector selector, long idleMsec) {
    this.selector = selector;
    this.idleMsec = idleMsec;
  }

  public void register(EventSource source) throws ClosedChannelException {
    SelectionKey result = source.getChannel().register(selector, SelectionKey.OP_READ);
    result.attach(source);
    sources.add(source);
    source.setSelectionKey(result);
  }

  public void process() throws IOException {
    for (;;) {
      int numKeys = selector.select(idleMsec);

      if (selector.keys().isEmpty())
        break;

      if (numKeys == 0) {
        for (EventSource source : sources) {
          source.timeout();
        }
        continue;
      }

      Set<SelectionKey> selectedKeys = selector.selectedKeys();
      Iterator<SelectionKey> it = selectedKeys.iterator();
      while (it.hasNext()) {
        SelectionKey key = it.next();
        EventSource source = (EventSource) key.attachment();
        if (key.isReadable())
          source.read(key);
        it.remove();
      }
    }
  }
}
