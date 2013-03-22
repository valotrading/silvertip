/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
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
    @Override public void connected(Connection<Message> connection) {}

    @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
      Assert.fail("messages detected");
      connection.close();
    }

    @Override public void closed(Connection<Message> connection) {}

    @Override public void garbledMessage(Connection<Message> connection, String message, byte[] data) {
      Assert.fail("garbled message detected");
      connection.close();
    }

    @Override public void sent(ByteBuffer buffer) { }
  }

  @Test
  public void garbledMessage() throws Exception {
    final String message = "The quick brown fox jumps over the lazy dog";
    final AtomicReference<String> garbledMessageData = new AtomicReference<String>(null);

    Callback callback = new Callback() {
      @Override public void garbledMessage(Connection<Message> connection, String message, byte[] data) {
        garbledMessageData.set(new String(data));
        connection.close();
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

    MessageParser<Message> parser = new MessageParser<Message>() {
      @Override public Message parse(ByteBuffer buffer) throws PartialMessageException {
        throw new PartialMessageException();
      }
    };

    sendMessage(message, new Callback(), parser);
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
  public void closed() throws Exception {
    final String message = "The quick brown fox jumps over the lazy dog";
    final AtomicBoolean connectionClosed = new AtomicBoolean(false);
    final AtomicReference<String> receivedMessage = new AtomicReference(null);

    Callback callback = new Callback() {
      @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
        if (messages.hasNext())
          receivedMessage.set(messages.next().toString());
      }

      @Override public void closed(Connection<Message> connection) {
        connectionClosed.set(true);
      }
    };

    MessageParser<Message> parser = new MessageParser<Message>() {
      @Override public Message parse(ByteBuffer buffer) throws PartialMessageException {
        byte[] message = new byte[buffer.limit() - buffer.position()];
        buffer.get(message);
        return new Message(message);
      }
    };

    sendMessage(message, callback, parser, TestServer.OPTION_CLOSE);

    Assert.assertTrue("callback not called", connectionClosed.get());
    Assert.assertEquals(message, receivedMessage.get());
  }

  @Test
  public void denied() throws Exception {
    final String message = "The quick brown fox jumps over the lazy dog";
    final AtomicBoolean connectionClosed = new AtomicBoolean(false);

    Callback callback = new Callback() {
      @Override public void closed(Connection<Message> connection) {
        connectionClosed.set(true);
      }
    };

    sendMessage(message, callback, null, TestServer.OPTION_DENY, 3);

    Assert.assertTrue("callback not called", connectionClosed.get());
  }

  private void sendMessage(String message, Connection.Callback<Message> callback, MessageParser<Message> parser)
      throws InterruptedException, IOException {
    sendMessage(message, callback, parser, 0, 1);
  }

  private void sendMessage(String message, Connection.Callback<Message> callback, MessageParser<Message> parser,
      int options) throws InterruptedException, IOException {
    sendMessage(message, callback, parser, options, 1);
  }

  private void sendMessage(String message, Connection.Callback<Message> callback, MessageParser<Message> parser,
      int options, int rounds) throws InterruptedException, IOException {
    final int port = getRandomPort();
    TestServer server = new TestServer(port, message, options);
    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    try {
      for (int i = 0; i < rounds; i++) {
        Connection<Message> connection = Connection.connect(new InetSocketAddress("localhost", port), parser, callback);
        Events events = Events.open();
        events.register(connection);

        long started = System.currentTimeMillis();
        long now;
        do {
          if (!events.process(IDLE_MSEC))
            break;

          now = System.currentTimeMillis();
        } while (now - started < IDLE_MSEC);

        connection.close();
      }
    } finally {
      server.stop();
      server.awaitForStop();
    }
  }

  private int getRandomPort() {
    return new Random(System.currentTimeMillis()).nextInt(1024) + 1024;
  }

  private final class TestServer implements Runnable {
    public static final int OPTION_CLOSE = 0x01;
    public static final int OPTION_DENY = 0x02;

    private final CountDownLatch serverStopped = new CountDownLatch(1);
    private final CountDownLatch serverStarted = new CountDownLatch(1);
    private final Server server;
    private final int options;

    public TestServer(int port, String message, int options) throws IOException {
      this.options = options;
      this.server = serve(message, port);
    }

    private Server serve(final String message, int port) throws IOException {
      final Connection.Callback<String> callback = new Connection.Callback<String>() {
        @Override public void connected(Connection<String> connection) {
          connection.send(message.getBytes());

          if ((options & OPTION_CLOSE) != 0)
            connection.close();
        }

        @Override public void messages(Connection<String> connection, Iterator<String> messages) {}
        @Override public void closed(Connection<String> connection) {}
        @Override public void garbledMessage(Connection<String> connection, String garbledMessage, byte[] data) {}
        @Override public void sent(ByteBuffer buffer) {}
      };

      Server server = Server.accept(port, new Server.ConnectionFactory<String>() {
        @Override public Connection<String> newConnection(SocketChannel channel) {
          if ((options & OPTION_DENY) != 0)
            return null;
          else
            return new Connection(channel, null, callback);
        }
      });

      return server;
    }

    public void awaitForStart() throws InterruptedException {
      serverStarted.await();
    }

    public void stop() throws IOException {
      server.close();
    }

    public void awaitForStop() throws InterruptedException {
      serverStopped.await();
    }

    @Override public void run() {
      serverStarted.countDown();

      try {
        Events events = Events.open();
        events.register(server);
        while (true) {
          if (!events.process(IDLE_MSEC))
            break;
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        serverStopped.countDown();
      }
    }
  }
}
