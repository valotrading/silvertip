package silvertip.spikes;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.Iterator;
import java.util.Set;

public class EchoServer {
  private static int PORT = 8080;

  public static void main(String[] args) throws IOException {
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    ServerSocket socket = serverChannel.socket();
    socket.bind(new InetSocketAddress(PORT));
    Selector selector = Selector.open();
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);

    for (;;) {
      selector.select();

      Set<SelectionKey> selectedKeys = selector.selectedKeys();
      Iterator<SelectionKey> it = selectedKeys.iterator();
      while (it.hasNext()) {
        SelectionKey key = (SelectionKey) it.next();
        int ops = key.readyOps();
        if ((ops & SelectionKey.OP_READ) == SelectionKey.OP_READ)
          read(key);
        if ((ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE)
          write(key);
        if ((ops & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT)
          accept(selector, key);
        it.remove();
      }
    }
  }

  private static void accept(Selector selector, SelectionKey acceptKey) throws IOException {
    ServerSocketChannel serverChannel = (ServerSocketChannel) acceptKey.channel();
    SocketChannel channel = serverChannel.accept();
    channel.configureBlocking(false);
    Socket sock = channel.socket();
    sock.setKeepAlive(true);
    ByteBuffer data = ByteBuffer.allocate(1024);
    data.position(data.limit());
    SelectionKey key = channel.register(selector, SelectionKey.OP_READ, null);
    Session session = new Session(key);
    key.attach(session);
  }

  private static void write(SelectionKey key) {
    Session session = (Session) key.attachment();
    session.write();
  }

  private static void read(SelectionKey key) throws IOException {
    Session session = (Session) key.attachment();
    session.read();
  }

  private static class Session {
    private final ByteBuffer recvBuffer = ByteBuffer.allocate(8196);
    private final SelectionKey key;
    private CharsetDecoder decoder;

    public Session(SelectionKey key) {
      Charset charset = Charset.forName("ISO-8859-1");
      decoder = charset.newDecoder();
      this.key = key;
    }

    public void write() {
      System.out.println("write");
    }

    public void read() throws IOException {
      SocketChannel sc = (SocketChannel) key.channel();
      if (sc.isOpen()) {
        int len;
        recvBuffer.clear();
        try {
          len = sc.read(recvBuffer);
        } catch (IOException e) {
          e.printStackTrace();
          len = -1;
        }

        if (len > 0) {
          recvBuffer.flip();
          CharBuffer buf = null;
          try {
            buf = decoder.decode(recvBuffer);
          } catch (Exception ce) {
            ce.printStackTrace();
            len = -1;
          }
          System.out.println(buf.toString());
        }
        if (len < 0)
          close();
      } else
        System.out.println("read closed");
    }

    private void close() throws IOException {
      SocketChannel sc = (SocketChannel) key.channel();
      if (sc.isOpen()) {
        sc.close();
        key.selector().wakeup();
        key.attach(null);
      }
    }
  }
}