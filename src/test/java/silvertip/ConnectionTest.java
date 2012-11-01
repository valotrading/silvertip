package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;

public class ConnectionTest {
  private static final int IDLE_MSEC = 50;

  private static class Callback implements Connection.Callback<Message> {
    @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
      Assert.fail("messages detected");
      connection.close();
    }

    @Override public void idle(Connection<Message> connection) {
      Assert.fail("idle detected");
      connection.close();
    }

    @Override public void closed(Connection<Message> connection) {}

    @Override public void garbledMessage(Connection<Message> connection, String message, byte[] data) {
      Assert.fail("garbled message detected");
      connection.close();
    }
  }

  @Test
  public void garbledMessage() throws Exception {
    final String message = "The quick brown fox jumps over the lazy dog";
    final AtomicReference<String> garbledMessageData = new AtomicReference<String>(null);

    Callback callback = new Callback() {
      @Override public void idle(Connection<Message> connection) {
        connection.close();
      }

      @Override public void garbledMessage(Connection<Message> connection, String message, byte[] data) {
        garbledMessageData.set(new String(data));
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

    Assert.assertEquals(message, garbledMessageData.get());
  }

  @Test
  public void partialMessage() throws Exception {
    final String message = "The quick brown fox...";

    Callback callback = new Callback() {
      @Override public void idle(Connection<Message> connection) {
        connection.close();
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
    final AtomicReference<String> receivedMessages = new AtomicReference("");

    Callback callback = new Callback() {
      @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
        while (messages.hasNext()) {
          receivedMessages.set(receivedMessages.get() + messages.next());
        }

        if (receivedMessages.get().length() == message.length())
          connection.close();
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

    Assert.assertEquals(message, receivedMessages.get());
  }

  @Test
  public void idle() throws Exception {
    Callback callback = new Callback() {
      long before = System.nanoTime();
      int count;

      @Override public void idle(Connection<Message> connection) {
        long now = System.nanoTime();
        Assert.assertTrue("spurious timeout detected", TimeUnit.NANOSECONDS.toMillis(now - before) >= IDLE_MSEC);
        if (count++ == 5)
          connection.close();
        before = now;
      }
    };

    sendMessage("", callback, null);
  }

  @Test
  public void closed() throws Exception {
    final AtomicBoolean connectionClosed = new AtomicBoolean(false);

    Callback callback = new Callback() {
      @Override public void closed(Connection<Message> connection) {
        connectionClosed.set(true);
      }
    };

    sendMessage("", callback, null, TestServer.OPTION_CLOSE);

    Assert.assertTrue("callback not called", connectionClosed.get());
  }

  private void sendMessage(String message, Connection.Callback<Message> callback, MessageParser<Message> parser)
      throws InterruptedException, IOException {
    sendMessage(message, callback, parser, 0);
  }

  private void sendMessage(String message, Connection.Callback<Message> callback, MessageParser<Message> parser,
      int options) throws InterruptedException, IOException {
    final int port = getRandomPort();
    TestServer server = new TestServer(port, message, options);
    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    try {
      final Connection<Message> connection = Connection.attemptToConnect(new InetSocketAddress("localhost", port), parser, callback);
      Events events = Events.open();
      events.register(connection);
      events.dispatch(IDLE_MSEC);
    } finally {
      server.awaitForStop();
    }
  }

  private int getRandomPort() {
    return new Random(System.currentTimeMillis()).nextInt(1024) + 1024;
  }

  private final class TestServer implements Runnable {
    public static final int OPTION_CLOSE = 0x01;

    private final CountDownLatch serverStopped = new CountDownLatch(1);
    private final CountDownLatch serverStarted = new CountDownLatch(1);
    private final String message;
    private final int port;
    private final int options;

    public TestServer(int port, String message, int options) {
      this.message = message;
      this.port = port;
      this.options = options;
    }

    public void awaitForStart() throws InterruptedException {
      serverStarted.await();
    }

    public void awaitForStop() throws InterruptedException {
      serverStopped.await();
    }

    @Override public void run() {
      Connection.Callback<String> callback = new Connection.Callback<String>() {
        @Override public void messages(Connection<String> connection, Iterator<String> messages) {}
        @Override public void idle(Connection<String> connection) {}
        @Override public void closed(Connection<String> connection) {}
        @Override public void garbledMessage(Connection<String> connection, String garbledMessage, byte[] data) {}
      };
      Connection<String> connection = null;
      try {
        serverStarted.countDown();
        connection = Connection.accept(new InetSocketAddress(port), null, callback);
        sendMessage(connection);
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
      if ((options & OPTION_CLOSE) != 0)
        events.stop();
      else
        events.dispatch(IDLE_MSEC);
    }
  }
}
