package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;

public class ConnectionSendTest {
  /*
   * The purpose of this test case is to send as much data as possible so that
   * we'll trigger a partial write condition (i.e. channel.write() is unable to
   * consume the whole byte buffer). The server side of this test case then
   * makes sure the received data is not corrupted.
   */
  @Test public void testPartialWrite() throws Exception {
    final int port = 4444;
    StubServer server = new StubServer(port);
    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    final MessageParser parser = new MessageParser() {
      @Override
      public Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException {
        Assert.fail();
        return null;
      }
    };
    final Connection.Callback callback = new Connection.Callback() {
      private int count;

      @Override
      public void idle(Connection connection) {
        Message message = newMessage();
        for (int i = 0; i < 10; i++) {
          connection.send(message);
        }
        if (++count == 10)
          connection.close();
      }

      private Message newMessage() {
        byte[] m = new byte[256*8];
        for (int j = 0; j < m.length; j++) {
          m[j] = (byte) (j % 256);
        }
        return new Message(m);
      }

      @Override
      public void messages(Connection connection, Iterator<Message> messages) {
        Assert.fail();
      }

      @Override public void closed(Connection connection) {
      }
    };

    final Events events = Events.open(100);
    final Connection connection = Connection.connect(new InetSocketAddress("localhost", port), parser, callback);
    events.register(connection);
    events.dispatch();
    server.awaitForStop();
  }

  private final class StubServer implements Runnable {
    private final CountDownLatch serverStopped = new CountDownLatch(1);
    private final CountDownLatch serverStarted = new CountDownLatch(1);
    private final int port;

    private StubServer(int port) {
      this.port = port;
    }

    public void awaitForStart() throws InterruptedException {
      serverStarted.await();
    }

    public void awaitForStop() throws InterruptedException {
      serverStopped.await();
    }

    @Override
    public void run() {
      ServerSocket serverSocket = null;
      try {
        serverSocket = new ServerSocket(port);
        serverStarted.countDown();
        Socket clientSocket = serverSocket.accept();
        int count = 0;
        for (;;) {
          int ch = clientSocket.getInputStream().read();
          if (ch == -1)
            break;
          Assert.assertEquals(count++ % 256, ch);
        }
      } catch (IOException e) {
        /* EOF */
      } finally {
        if (serverSocket != null) {
          try {
            serverSocket.close();
          } catch (IOException ex) {
          }
        }
        serverStopped.countDown();
      }
    }
  }
}