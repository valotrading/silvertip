package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

public class ConnectionTest {
  private static final int IDLE_MSEC = 50;

  @Test
  public void garbledMessage() throws Exception {
    final String message = "The quick brown fox jumps over the lazy dog";

    Connection.Callback<Message> callback = new Connection.Callback<Message>() {
      @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.fail("messages detected");
      }

      @Override public void idle(Connection<Message> connection) {
        connection.close();
      }

      @Override public void closed(Connection<Message> connection) {}

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
      @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.fail("partial message detected");
      }

      @Override public void idle(Connection<Message> connection) {
        connection.close();
      }

      @Override public void closed(Connection<Message> connection) {}

      @Override public void garbledMessage(String message, byte[] data) {
        Assert.fail("garbled message detected");
      }
    };

    MessageParser<Message> parser = new MessageParser<Message>() {
      @Override public Message parse(ByteBuffer buffer) throws PartialMessageException {
        throw new PartialMessageException();
      }
    };

    sendMessage(message, callback, parser);
  }

  @Test
  public void multipleMessages() throws Exception {
    final String message = "ABC";

    Connection.Callback<Message> callback = new Connection.Callback<Message>() {
      @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.assertEquals("A", messages.next().toString());
        Assert.assertEquals("B", messages.next().toString());
        Assert.assertEquals("C", messages.next().toString());
        Assert.assertFalse(messages.hasNext());
        connection.close();
      }

      @Override public void idle(Connection<Message> connection) {
        Assert.fail("idle detected");
      }

      @Override public void closed(Connection<Message> connection) {}

      @Override public void garbledMessage(String message, byte[] data) {
        Assert.fail("garbled message detected");
      }
    };

    MessageParser<Message> parser = new MessageParser<Message>() {
      @Override public Message parse(ByteBuffer buffer) throws PartialMessageException {
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

      @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.fail("messages detected");
      }

      @Override public void idle(Connection<Message> connection) {
        long now = System.nanoTime();
        Assert.assertTrue("spurious timeout detected", TimeUnit.NANOSECONDS.toMillis(now - before) >= IDLE_MSEC);
        if (count++ == 5)
          connection.close();
        before = now;
      }

      @Override public void closed(Connection<Message> connection) {}

      @Override public void garbledMessage(String message, byte[] data) {
        Assert.fail("garbled message detected");
      }
    };

    sendMessage("", callback, null);
  }

  @Test
  public void closed() throws Exception {
    final AtomicBoolean connectionClosed = new AtomicBoolean(false);
    final int port = getRandomPort();
    final StubServer server = new StubServer(port, "", false);

    Connection.Callback<Message> callback = new Connection.Callback<Message>() {
      @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
        Assert.fail("messages detected");
      }

      @Override public void idle(Connection<Message> connection) {
        server.notifyClientStopped();
      }

      @Override public void closed(Connection<Message> connection) {
        connectionClosed.set(true);
      }

      @Override public void garbledMessage(String message, byte[] data) {
        Assert.fail("garbled message detected");
      }
    };

    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    try {
      final Connection<Message> connection = Connection.attemptToConnect(new InetSocketAddress("localhost", port), null, callback);
      Events events = Events.open();
      events.register(connection);
      events.dispatch(IDLE_MSEC);
    } finally {
      server.awaitForStop();
    }

    Assert.assertTrue("callback not called", connectionClosed.get());
  }

  private void sendMessage(final String message, Connection.Callback<Message> callback, MessageParser<Message> parser)
      throws InterruptedException, IOException {
    final int port = getRandomPort();
    StubServer server = new StubServer(port, message);
    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    try {
      final Connection<Message> connection = Connection.attemptToConnect(new InetSocketAddress("localhost", port), parser, callback);
      Events events = Events.open();
      events.register(connection);
      events.dispatch(IDLE_MSEC);
    } finally {
      server.notifyClientStopped();
      server.awaitForStop();
    }
  }

  private int getRandomPort() {
    return new Random(System.currentTimeMillis()).nextInt(1024) + 1024;
  }

  private final class StubServer implements Runnable {
    private final CountDownLatch serverStopped = new CountDownLatch(1);
    private final CountDownLatch serverStarted = new CountDownLatch(1);
    private final CountDownLatch clientStopped = new CountDownLatch(1);
    private final String message;
    private final int port;
    private final boolean linger;

    private StubServer(int port, String message) {
      this(port, message, true);
    }

    private StubServer(int port, String message, boolean linger) {
      this.message = message;
      this.port = port;
      this.linger = linger;
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

    @Override public void run() {
      Connection.Callback<String> callback = new Connection.Callback<String>() {
        @Override public void messages(Connection<String> connection, Iterator<String> messages) {}
        @Override public void idle(Connection<String> connection) {}
        @Override public void closed(Connection<String> connection) {}
        @Override public void garbledMessage(String garbledMessage, byte[] data) {}
      };
      Connection<String> connection = null;
      try {
        serverStarted.countDown();
        connection = Connection.accept(new InetSocketAddress(port), null, callback);
        sendMessage(connection);
        clientStopped.await();
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        if (connection != null) {
          connection.close();
        }
        serverStopped.countDown();
      }
    }

    private void sendMessage(Connection connection) throws IOException {
      Events events = Events.open();
      events.register(connection);
      connection.send(message.getBytes());
      if (linger) {
        events.dispatch(IDLE_MSEC);
      } else {
        events.stop();
      }
    }
  }
}
