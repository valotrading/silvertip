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
    final CountDownLatch serverStarted = new CountDownLatch(1);
    final CountDownLatch serverStopped = new CountDownLatch(1);
    final CountDownLatch clientStopped = new CountDownLatch(1);
    Thread server = new Thread(new Runnable() {
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
    });
    server.start();
    serverStarted.await();
    try {
      final Connection connection = Connection.connect(new InetSocketAddress("localhost", port), 1000,
          new MessageParser() {
            @Override
            public Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException {
              throw new GarbledMessageException();
            }
          });
      connection.wait(new Callback() {
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
      clientStopped.countDown();
      serverStopped.await();
    }
  }
}
