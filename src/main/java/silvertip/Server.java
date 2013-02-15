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
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Server implements EventSource {
  public interface ConnectionFactory<T> {
    Connection<T> newConnection(SocketChannel channel);
  }

  private final ServerSocketChannel serverChannel;
  private final ConnectionFactory<?> factory;

  public static Server accept(int port, ConnectionFactory<?> factory) throws IOException {
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    serverChannel.socket().setReuseAddress(true);
    serverChannel.socket().bind(new InetSocketAddress(port));
    return new Server(serverChannel, factory);
  }

  public Server(ServerSocketChannel serverChannel, ConnectionFactory<?> factory) {
    this.serverChannel = serverChannel;
    this.factory = factory;
  }

  public void close() throws IOException {
    serverChannel.close();
  }

  @Override public SelectionKey register(Selector selector, int ops) throws IOException {
    return serverChannel.register(selector, SelectionKey.OP_ACCEPT);
  }

  @Override public void unregister() {
  }

  @Override public void read(SelectionKey key) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public void write(SelectionKey key) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public EventSource accept(SelectionKey key) throws IOException {
    ServerSocketChannel sch = (ServerSocketChannel) key.channel();
    SocketChannel channel = sch.accept();
    if (channel == null)
      return null;

    channel.configureBlocking(false);

    Connection connection = factory.newConnection(channel);
    if (connection == null)
      SocketChannels.close(channel);

    return connection;
  }

  @Override public void timeout() {
  }

  @Override public boolean isClosed() {
    return !serverChannel.isOpen();
  }
}
