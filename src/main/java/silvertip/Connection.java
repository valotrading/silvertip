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
import java.util.Collections;

public class Connection<T> implements EventSource {
  public interface Callback<T> {
    void connected(Connection<T> connection);

    void message(Connection<T> connection, T message);

    void closed(Connection<T> connection);

    void garbledMessage(Connection<T> connection, String message, byte[] data);

    void sent(ByteBuffer buffer);
  }

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
      rxBuffer.flip();
      while (rxBuffer.hasRemaining()) {
        rxBuffer.mark();
        try {
          callback.message(this, parser.parse(rxBuffer));
        } catch (PartialMessageException e) {
          rxBuffer.reset();
          break;
        } catch (GarbledMessageException e) {
          callback.garbledMessage(this, e.getMessage(), e.getMessageData());
        }
      }
      rxBuffer.compact();
    } else if (len < 0) {
      close();
    }
  }

  public void send(byte[] byteArray) {
    send(ByteBuffer.wrap(byteArray));
  }

  public void send(Message message) {
    send(message.toByteBuffer());
  }

  public void send(ByteBuffer buffer) {
    callback.sent(buffer);

    if (selectionKey == null)
      throw new IllegalStateException("Connection is not registered");
    try {
      while (buffer.hasRemaining())
        channel.write(buffer);
    } catch (IOException e) {
      close();
    }
  }

  public void close() {
    if (events != null)
      events.unregister(this);

    callback.closed(this);

    SocketChannel sc = (SocketChannel) selectionKey.channel();
    SocketChannels.close(sc);

    selectionKey.attach(null);
    selectionKey.cancel();
    selectionKey.selector().wakeup();
  }

  @Override public EventSource accept() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public boolean isClosed() {
    return !channel.isOpen();
  }
}
