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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Connection<T> implements EventSource {
  public interface Callback<T> {
    void connected(Connection<T> connection);

    void messages(Connection<T> connection, Iterator<T> messages);

    void closed(Connection<T> connection);

    void garbledMessage(Connection<T> connection, String message, byte[] data);

    void sent(ByteBuffer buffer);
  }

  private List<ByteBuffer> txBuffers = new LinkedList<ByteBuffer>();
  private ByteBuffer rxBuffer = ByteBuffer.allocate(4096);
  private SelectionKey selectionKey;
  private SocketChannel channel;
  private Events events;
  private MessageParser<T> parser;
  private Callback<T> callback;

  public static <T> Connection<T> connect(InetSocketAddress address, MessageParser<T> parser, Callback<T> callback)
      throws IOException {
    SocketChannel channel = SocketChannel.open();
    channel.connect(address);
    channel.configureBlocking(false);
    return new Connection<T>(channel, parser, callback);
  }

  public Connection(SocketChannel channel, MessageParser<T> parser, Callback<T> callback) {
    this.channel = channel;
    this.callback = callback;
    this.parser = parser;
  }

  @Override public SelectionKey register(Events events) throws IOException {
    this.selectionKey = channel.register(events.selector(), SelectionKey.OP_READ);
    this.events = events;

    callback.connected(this);

    return selectionKey;
  }

  @Override public void read() throws IOException {
    int len;
    try {
      len = channel.read(rxBuffer);
    } catch (IOException e) {
      len = -1;
    }
    if (len > 0) {
      Iterator<T> messages = parse();
      if (messages.hasNext()) {
        callback.messages(this, messages);
      }
    } else if (len < 0) {
      close();
    }
  }

  private Iterator<T> parse() throws IOException {
    rxBuffer.flip();
    List<T> result = new ArrayList<T>();
    while (rxBuffer.hasRemaining()) {
      rxBuffer.mark();
      try {
        result.add(parser.parse(rxBuffer));
      } catch (PartialMessageException e) {
        rxBuffer.reset();
        break;
      } catch (GarbledMessageException e) {
        callback.garbledMessage(this, e.getMessage(), e.getMessageData());
      }
    }
    rxBuffer.compact();
    return result.iterator();
  }

  public void send(byte[] byteArray) {
    send(ByteBuffer.wrap(byteArray));
  }

  public void send(Message message) {
    send(message.toByteBuffer());
  }

  public void send(ByteBuffer buffer) {
    callback.sent(buffer);
    txBuffers.add(buffer);
    if (selectionKey == null)
      throw new IllegalStateException("Connection is not registered");
    try {
      flush();
    } catch (IOException e) {
      close();
    }
  }

  @Override public void write() throws IOException {
    try {
      flush();
    } catch (IOException e) {
      close();
    }
    if (txBuffers.isEmpty())
      selectionKey.interestOps(SelectionKey.OP_READ);
  }

  public void close() {
    if (events != null)
      events.unregister(this);

    callback.closed(this);

    try {
      while (!txBuffers.isEmpty())
        flush();
    } catch (IOException e) {
    }

    SocketChannel sc = (SocketChannel) selectionKey.channel();
    SocketChannels.close(sc);

    selectionKey.attach(null);
    selectionKey.cancel();
    selectionKey.selector().wakeup();
  }

  private void flush() throws IOException {
    while (!txBuffers.isEmpty()) {
      ByteBuffer txBuffer = txBuffers.get(0);
      if (!write(txBuffer)) {
        selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        selectionKey.selector().wakeup();
        break;
      }
      txBuffers.remove(0);
    }
  }

  private boolean write(ByteBuffer txBuffer) throws IOException {
    while (txBuffer.hasRemaining()) {
      if (channel.write(txBuffer) == 0) {
        return false;
      }
    }
    return true;
  }

  @Override public EventSource accept() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public boolean isClosed() {
    return !channel.isOpen();
  }
}
