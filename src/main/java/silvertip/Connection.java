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

public class Connection implements EventSource {
  public interface Callback {
    void messages(Connection connection, Iterator<Message> messages);

    void idle(Connection connection);
  }

  private ByteBuffer rxBuffer = ByteBuffer.allocate(4096);
  private SelectionKey selectionKey;
  private SocketChannel channel;
  private MessageParser parser;
  private Callback callback;

  public static Connection connect(InetSocketAddress address, MessageParser parser, Callback callback)
      throws IOException {
    SocketChannel channel = SocketChannel.open();
    channel.connect(address);
    channel.configureBlocking(false);
    return new Connection(channel, parser, callback);
  }

  public Connection(SocketChannel channel, MessageParser parser, Callback callback) {
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

  public void send(Message message) {
    try {
      channel.write(message.toByteBuffer());
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
        Iterator<Message> messages = parse();
        if (messages.hasNext()) {
          callback.messages(this, messages);
        }
      } else if (len < 0) {
        close();
      }
    }
  }

  private Iterator<Message> parse() throws IOException {
    rxBuffer.flip();
    List<Message> result = new ArrayList<Message>();
    while (rxBuffer.hasRemaining()) {
      rxBuffer.mark();
      try {
        result.add(parser.parse(rxBuffer));
      } catch (PartialMessageException e) {
        rxBuffer.reset();
        break;
      } catch (GarbledMessageException e) {
        rxBuffer.reset();
        byte[] data = new byte[rxBuffer.limit() - rxBuffer.position()];
        rxBuffer.get(data);
        result.add(new Message(data));
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
