package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Connection<T extends Message> implements EventSource {
  public interface Callback<T extends Message> {
    void messages(Connection<T> connection, Iterator<T> messages);

    void idle(Connection<T> connection);

    void closed(Connection<T> connection);
  }

  private ByteBuffer rxBuffer = ByteBuffer.allocate(4096);
  private SelectionKey selectionKey;
  private SocketChannel channel;
  private MessageParser<T> parser;
  private Callback<T> callback;

  public static <T extends Message> Connection<T> connect(InetSocketAddress address, MessageParser<T> parser, Callback<T> callback)
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

  @Override
  public void timeout() {
    callback.idle(this);
  }

  public SelectionKey register(Selector selector, int ops) throws IOException {
    return selectionKey = channel.register(selector, ops);
  }

  @Override public void unregister() {
    callback.closed(this);
  }

  public void send(Message message) {
    try {
      ByteBuffer byteBuffer = message.toByteBuffer();
      while (byteBuffer.hasRemaining()) {
        channel.write(byteBuffer);
      }
    } catch (IOException e) {
      close();
    }
  }

  @Override
  public void read(SelectionKey key) throws IOException {
    SocketChannel sc = (SocketChannel) key.channel();
    if (sc.isOpen()) {
      int len;
      try {
        len = sc.read(rxBuffer);
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
  }

  @Override public EventSource accept(SelectionKey key) throws IOException {
    throw new UnsupportedOperationException();
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
      }
    }
    rxBuffer.compact();
    return result.iterator();
  }

  public void close() {
    selectionKey.attach(null);
    selectionKey.cancel();
    SocketChannel sc = (SocketChannel) selectionKey.channel();
    try {
      sc.close();
    } catch (IOException e) {
    }
    selectionKey.selector().wakeup();
  }

  public boolean isClosed() {
    SocketChannel sc = (SocketChannel) selectionKey.channel();
    return !sc.isOpen();
  }
}
