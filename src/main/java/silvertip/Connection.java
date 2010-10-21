package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Connection<T> implements EventSource {
  public interface Callback<T> {
    void messages(Connection<T> connection, Iterator<T> messages);

    void idle(Connection<T> connection);

    void closed(Connection<T> connection);
  }

  private List<ByteBuffer> txBuffers = Collections.synchronizedList(new ArrayList<ByteBuffer>());
  private ByteBuffer rxBuffer = ByteBuffer.allocate(4096);
  private SelectionKey selectionKey;
  private SocketChannel channel;
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

  @Override public SelectionKey register(Selector selector, int ops) throws IOException {
    return selectionKey = channel.register(selector, ops);
  }

  @Override public void unregister() {
    callback.closed(this);
  }

  @Override public void read(SelectionKey key) throws IOException {
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
        /* Ignore garbled message */
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
    txBuffers.add(buffer);
    if (selectionKey == null)
      throw new IllegalStateException("Connection is not registered");
    selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
  }

  @Override public void write(SelectionKey key) throws IOException {
    try {
      flush();
    } catch (IOException e) {
      close();
    }
    if (txBuffers.isEmpty())
      key.interestOps(SelectionKey.OP_READ);
  }

  public void close() {
    try {
      while (!txBuffers.isEmpty())
        flush();
    } catch (IOException e) {
    }
    selectionKey.attach(null);
    selectionKey.cancel();
    SocketChannel sc = (SocketChannel) selectionKey.channel();
    try {
      sc.close();
    } catch (IOException e) {
    }
    selectionKey.selector().wakeup();
  }

  private void flush() throws IOException {
    Iterator<ByteBuffer> it = txBuffers.iterator();
    while (it.hasNext()) {
      ByteBuffer txBuffer = it.next();
      if (!write(txBuffer))
        break;
      it.remove();
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

  @Override public void timeout() {
    callback.idle(this);
  }

  @Override public EventSource accept(SelectionKey key) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public boolean isClosed() {
    SocketChannel sc = (SocketChannel) selectionKey.channel();
    return !sc.isOpen();
  }
}
