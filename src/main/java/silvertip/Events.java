package silvertip;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The <code>Events</code> class is the heart of Silvertip, an event
 * notification API for Java. The class is a wrapper on top of NIO
 * <code>Selector</code> and can be used for polling one or more event sources
 * (TCP sockets, for example) as efficiently as possible. On Linux, the JVM uses
 * <code>epoll_ctl(2)</code> and <code>epoll_wait(2)</code> system calls to
 * implement <code>Events#register</code> and <code>Events#dispatch</code>
 * methods, respectively.
 * <p>
 * To use the API, you need to instantiate a new <code>Events</code> object and
 * register one or more <code>EventSource</code>s to it. You can then invoke
 * <code>Events#dispatch</code> method to enter event dispatch loop that returns
 * only if all event sources unregister themselves or you invoke the
 * <code>Events#stop</code> method.
 * <p>
 * A simple example looks like this:
 *
 * <pre>
 *   InetSocketAddress address = ...;
 *   MessageParser parser = new MessageParser() {
 *     public Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException {
 *        return new Message(buffer.array());
 *      }
 *   };
 *   Events events = Events.open(30*1000); // 30 second timeout
 *   Connection connection = Connection.connect(address, parser, new Connection.Callback() {
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
 * <p>
 * The <code>MessageParser</code> interface is used for parsing a single message
 * from a byte buffer that may contain multiple messages, including a partial
 * message at the end of the buffer.
 */
public class Events {
  private List<EventSource> sources = new ArrayList<EventSource>();
  private Selector selector;
  private boolean stopped;

  public static Events open() throws IOException {
    return new Events(Selector.open());
  }

  public Events(Selector selector) {
    this.selector = selector;
  }

  public void register(EventSource source) throws IOException {
    SelectionKey result = source.register(selector, SelectionKey.OP_READ);
    result.attach(source);
    sources.add(source);
  }

  public void dispatch(long timeout) throws IOException {
    while (!isStopped()) {
      if (!process(timeout))
        break;
    }
  }

  public boolean process(long timeout) throws IOException {
    while (timeout > 0) {
      long start = System.nanoTime();
      int numKeys = selector.select(timeout);
      long end = System.nanoTime();

      unregisterClosed();

      if (selector.keys().isEmpty())
        return false;

      if (numKeys > 0) {
        dispatchMessages();
        break;
      }

      timeout -= TimeUnit.NANOSECONDS.toMillis(end - start);
      if (timeout <= 0) {
        timeout();
        break;
      }
    }
    return true;
  }

  public boolean processNow() throws IOException {
    int numKeys = selector.selectNow();

    unregisterClosed();

    if (selector.keys().isEmpty())
      return false;

    if (numKeys > 0)
      dispatchMessages();

    return true;
  }

  public boolean isStopped() {
    return stopped;
  }

  private void unregisterClosed() {
    Iterator<EventSource> it = sources.iterator();
    while (it.hasNext()) {
      EventSource source = it.next();
      if (source.isClosed()) {
        source.unregister();
        it.remove();
      }
    }
  }

  private void timeout() {
    Iterator<EventSource> it = sources.iterator();
    while (it.hasNext()) {
      EventSource source = it.next();
      source.timeout();
    }
  }

  private void dispatchMessages() throws IOException {
    List<EventSource> newSources = new ArrayList<EventSource>();
    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
      SelectionKey key = it.next();
      EventSource source = (EventSource) key.attachment();
      if (key.isValid()) {
        if (key.isAcceptable())
          newSources.add(source.accept(key));
        if (key.isReadable())
          source.read(key);
        else if (key.isWritable())
          source.write(key);
      }
      it.remove();
    }
    for (EventSource source : newSources)
      register(source);
  }

  public void stop() {
    stopped = true;
    selector.wakeup();
  }
}
