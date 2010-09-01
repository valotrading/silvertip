package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Server implements EventSource {
  public interface ConnectionFactory<T extends Message> {
    Connection<T> newConnection(SocketChannel channel);
  }

  private final ServerSocketChannel serverChannel;
  private final ConnectionFactory<?> factory;

  public static Server accept(int port, ConnectionFactory<?> factory) throws IOException {
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    serverChannel.socket().bind(new InetSocketAddress(port));
    return new Server(serverChannel, factory);
  }

  public Server(ServerSocketChannel serverChannel, ConnectionFactory<?> factory) {
    this.serverChannel = serverChannel;
    this.factory = factory;
  }

  @Override public SelectionKey register(Selector selector, int ops) throws IOException {
    return serverChannel.register(selector, SelectionKey.OP_ACCEPT);
  }

  @Override public void unregister() {
  }

  @Override public void read(SelectionKey key) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public EventSource accept(SelectionKey key) throws IOException {
    ServerSocketChannel sch = (ServerSocketChannel) key.channel();
    SocketChannel channel = sch.accept();
    channel.configureBlocking(false);
    return factory.newConnection(channel);
  }

  @Override public void timeout() {
  }

  @Override public boolean isClosed() {
    return !serverChannel.isOpen();
  }
}
