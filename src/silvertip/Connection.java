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
import java.util.Set;

public class Connection {
  private DelimitedMessageParser parser = new DelimitedMessageParser();
  private ByteBuffer rxBuffer = ByteBuffer.allocate(4096);
  private SocketChannel channel;
  private boolean closed;
  private long idleMsec;

  public static Connection connect(InetSocketAddress address, long idleMsec) throws IOException {
    SocketChannel channel = SocketChannel.open();
    channel.configureBlocking(false);
    channel.connect(address);
    channel.finishConnect();
    return new Connection(channel, idleMsec);
  }

  public Connection(SocketChannel channel, long idleMsec) {
    this.channel  = channel;
    this.idleMsec = idleMsec;
  }

  public void send(Message message) {
    try {
      channel.write(ByteBuffer.wrap(message.value().getBytes()));
    } catch (IOException e) {
      closed = true;
    }
  }

  public void wait(Callback callback) {
    try {
      Selector selector = Selector.open();
      SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
      while (!closed) {
        int numKeys = selector.select(idleMsec);

        if (numKeys == 0) {
          callback.idle();
          continue;
        }

        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> it = selectedKeys.iterator();
        while (it.hasNext()) {
          key = (SelectionKey) it.next();
          int ops = key.readyOps();
          if ((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ)
            read(callback, key);
          it.remove();
        }
      }
      close(key);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void read(Callback callback, SelectionKey key) throws Exception {
    SocketChannel sc = (SocketChannel) key.channel();
    if (sc.isOpen()) {
      int len;
      try {
        len = sc.read(rxBuffer);
      } catch (IOException e) {
        len = -1;
      }
      if (len > 0) {
        callback.messages(parse());
      } else if (len < 0) {
        closed = true;
      }
    }
  }

  private Iterator<Message> parse() throws Exception {
    List<Message> result = new ArrayList<Message>();
    List<String> messages = parser.parse(rxBuffer);
    for (String m : messages) {
      result.add(new Message(m));
    }
    return result.iterator();
  }

  private static void close(SelectionKey key) throws IOException {
    SocketChannel sc = (SocketChannel) key.channel();
    if (sc.isOpen()) {
      sc.close();
      key.selector().wakeup();
      key.attach(null);
    }
  }
}
