package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import junit.framework.Assert;

import org.junit.Test;

public class ConnectionTest {
  @Test
  public void garbledMessage() throws Exception {
    final int port = 4444;
    final String message = "The quick brown fox jumps over the lazy dog";
    StubServer server = new StubServer(port, message);
    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    try {
      final Connection connection = Connection.connect(new InetSocketAddress("localhost", port), 1000,
          new MessageParser() {
            @Override
            public Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException {
              throw new GarbledMessageException();
            }
          });
      connection.wait(new Connection.Callback() {
        @Override
        public void messages(Iterator<Message> messages) {
          Message m = messages.next();
          Assert.assertFalse(messages.hasNext());
          Assert.assertEquals(message, m.toString());
          connection.close();
        }

        @Override
        public void idle() {
          Assert.fail("idle detected");
        }
      });
    } finally {
      server.notifyClientStopped();
      server.awaitForStop();
    }
  }

  private final class StubServer implements Runnable {
    private final CountDownLatch serverStopped = new CountDownLatch(1);
    private final CountDownLatch serverStarted = new CountDownLatch(1);
    private final CountDownLatch clientStopped = new CountDownLatch(1);
    private final String message;
    private final int port;

    private StubServer(int port, String message) {
      this.message = message;
      this.port = port;
    }

    public void awaitForStart() throws InterruptedException {
      serverStarted.await();
    }

    public void notifyClientStopped() {
      clientStopped.countDown();
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
        clientSocket.getOutputStream().write(message.getBytes());
        clientSocket.getOutputStream().flush();
        clientStopped.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
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
