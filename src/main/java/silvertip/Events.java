/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silvertip;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
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
 *   Events events = Events.open();
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
  private List<EventSource> newSources = new ArrayList<EventSource>();
  private Selector selector;

  public static Events open() throws IOException {
    return new Events(Selector.open());
  }

  public Events(Selector selector) {
    this.selector = selector;
  }

  public void close() throws IOException {
    selector.close();
  }

  public Selector selector() {
    return selector;
  }

  public void register(EventSource source) throws IOException {
    SelectionKey result = source.register(this);
    result.attach(source);
    sources.add(source);
  }

  public void unregister(EventSource source) {
    sources.remove(source);
  }

  public boolean process(long timeout) throws IOException {
    while (timeout > 0) {
      long start = System.nanoTime();
      int numKeys = selector.select(timeout);
      long end = System.nanoTime();

      if (selector.keys().isEmpty())
        return false;

      if (numKeys > 0) {
        dispatchMessages();
        break;
      }

      timeout -= TimeUnit.NANOSECONDS.toMillis(end - start);
      if (timeout <= 0) {
        break;
      }
    }
    return true;
  }

  public boolean processNow() throws IOException {
    int numKeys = selector.selectNow();

    if (selector.keys().isEmpty())
      return false;

    if (numKeys > 0)
      dispatchMessages();

    return true;
  }

  private void dispatchMessages() throws IOException {
    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
      SelectionKey key = it.next();
      EventSource source = (EventSource) key.attachment();

      if (key.isValid()) {
        try {
          if (key.isAcceptable()) {
            EventSource newSource = source.accept();
            if (newSource != null)
              newSources.add(newSource);
          }

          if (key.isReadable()) {
            source.read();
          }

          if (key.isWritable()) {
            source.write();
          }
        } catch (CancelledKeyException e) {
        }
      }

      it.remove();
    }
    for (int i = 0; i < newSources.size(); i++)
      register(newSources.get(i));

    if (!newSources.isEmpty())
      newSources.clear();
  }
}
