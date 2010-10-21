package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import org.junit.Test;

public class ConnectionTest {
  private static final int IDLE_MSEC = 50;

  private final Selector selector = Selector.open();

  public ConnectionTest() throws IOException {
  }

  @Test
  public void garbledMessage() throws Exception {
    final String message = "The quick brown fox jumps over the lazy dog";
    Connection.Callback<Message> callback = new Connection.Callback<Message>() {
      @Override
      public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.fail();
      }

      @Override
      public void idle(Connection<Message> connection) {
        connection.close();
      }

      @Override public void closed(Connection<Message> connection) {
      }

      @Override public void garbledMessage(String garbledMessage, byte[] data) {
        Assert.assertEquals(message, new String(data));
      }
    };
    MessageParser<Message> parser = new MessageParser<Message>() {
      @Override public Message parse(ByteBuffer buffer) throws GarbledMessageException, PartialMessageException {
        byte[] data = new byte[buffer.limit() - buffer.position()];
        buffer.get(data);
        throw new GarbledMessageException("garbled message", data);
      }
    };
    sendMessage(message, callback, parser);
  }

  @Test
  public void partialMessage() throws Exception {
    final String message = "The quick brown fox...";
    Connection.Callback<Message> callback = new Connection.Callback<Message>() {
      @Override
      public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.fail("partial message detected");
      }

      @Override
      public void idle(Connection<Message> connection) {
        connection.close();
      }

      @Override public void closed(Connection<Message> connection) {
      }

      @Override public void garbledMessage(String message, byte[] data) {
      }
    };
    MessageParser<Message> parser = new MessageParser<Message>() {
      @Override
      public Message parse(ByteBuffer buffer) throws PartialMessageException {
        throw new PartialMessageException();
      }
    };
    sendMessage(message, callback, parser);
  }

  @Test
  public void multipleMessages() throws Exception {
    final String message = "ABC";
    Connection.Callback<Message> callback = new Connection.Callback<Message>() {
      @Override
      public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.assertEquals("A", messages.next().toString());
        Assert.assertEquals("B", messages.next().toString());
        Assert.assertEquals("C", messages.next().toString());
        Assert.assertFalse(messages.hasNext());
        connection.close();
      }

      @Override
      public void idle(Connection<Message> connection) {
        Assert.fail("idle detected");
      }

      @Override public void closed(Connection<Message> connection) {
      }

      @Override public void garbledMessage(String message, byte[] data) {
      }
    };
    MessageParser<Message> parser = new MessageParser<Message>() {
      @Override
      public Message parse(ByteBuffer buffer) throws PartialMessageException {
        byte[] message = new byte[1];
        buffer.get(message);
        return new Message(message);
      }
    };
    sendMessage(message, callback, parser);
  }

  @Test
  public void idle() throws Exception {
    Connection.Callback<Message> callback = new Connection.Callback<Message>() {
      long before = System.nanoTime();
      int count;

      @Override
      public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.fail();
      }

      @Override public void idle(Connection<Message> connection) {
        long now = System.nanoTime();
        Assert.assertTrue("suprious timeout", TimeUnit.NANOSECONDS.toMillis(now - before) >= IDLE_MSEC);
        if (count++ == 5)
          connection.close();
        before = now;
        selector.wakeup();
      }

      @Override public void closed(Connection<Message> connection) {
      }

      @Override public void garbledMessage(String message, byte[] data) {
      }
    };
    sendMessage("", callback, null);
  }

  private void sendMessage(final String message, Connection.Callback<Message> callback, MessageParser<Message> parser)
      throws InterruptedException, IOException {
    final int port = 4444;
    StubServer server = new StubServer(port, message);
    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    try {
      final Connection<Message> connection = Connection.connect(new InetSocketAddress("localhost", port), parser, callback);
      Events events = new Events(selector, IDLE_MSEC);
      events.register(connection);
      events.dispatch();
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
