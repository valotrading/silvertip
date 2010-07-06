package silvertip;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * The <code>Events</code> class is the heart of Silvertip, an event
 * notification API for Java. The class is a wrapper on top of NIO
 * <code>Selector</code> and can be used for polling one or more event sources
 * (TCP sockets, for example) as efficiently as possible. On Linux, for example,
 * the JVM uses <code>epoll_ctl(2)</code> and <code>epoll_wait(2)</code>, under
 * the hood for <code>Events#register</code> and <code>Events#dispatch</code>,
 * respectively.
 * <p>
 * To use the API, you need to instantiate a new <code>Events</code> object and
 * register one or more <code>EventSource</code>s to it. You can then invoke
 * <code>Events#dispatch</code> to enter event dispatch loop that returns only
 * if all event sources unregister themselves or you invoke
 * <code>EventSource#stop</code>.
 * <p>
 * A simple example looks like this:
 *
 * <pre>
 *   Events events = Events.open(30*1000); // 30 second timeout
 *   Connection connection = Connection.connect(..., new Connection.Callback() {
 *     public void messages(Connection connection, Iterator<Message> messages) {
 *       while (messages.hasNext()) {
 *         System.out.println(messages.next());
 *       }
 *     }
 *     public void idle(Connection connection) {
 *       // This callback is called every 30 seconds if there's no activity.
 *     }
 *   }));
 *   events.register(connection);
 *   events.dispatch();
 * </pre>
 */
public class Events {
  private List<EventSource> sources = new ArrayList<EventSource>();
  private Selector selector;
  private boolean stopped;
  private long idleMsec;

  public static Events open(long idleMsec) throws IOException {
    return new Events(Selector.open(), idleMsec);
  }

  public Events(Selector selector, long idleMsec) {
    this.selector = selector;
    this.idleMsec = idleMsec;
  }

  public void register(EventSource source) throws IOException {
    SelectionKey result = source.register(selector, SelectionKey.OP_READ);
    result.attach(source);
    sources.add(source);
  }

  public void dispatch() throws IOException {
    while (!stopped) {
      int numKeys = selector.select(idleMsec);

      if (selector.keys().isEmpty())
        break;

      if (numKeys == 0) {
        Iterator<EventSource> it = sources.iterator();
        while (it.hasNext()) {
          EventSource source = it.next();
          if (source.isClosed()) {
            it.remove();
            continue;
          }
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

  public void stop() {
    stopped = true;
  }
}
