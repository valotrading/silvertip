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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;

public class ConnectionSendTest {
  private static final int IDLE_MSEC = 50;
  /*
   * The purpose of this test case is to send as much data as possible so that
   * we'll trigger a partial write condition (i.e. channel.write() is unable to
   * consume the whole byte buffer). The server side of this test case then
   * makes sure the received data is not corrupted.
   */
  @Test
  public void testPartialWrite() throws Exception {
    final int port = new Random(System.currentTimeMillis()).nextInt(1024) + 1024;
    final StubServer server = new StubServer(port);
    Thread serverThread = new Thread(server);
    serverThread.start();
    server.awaitForStart();
    final MessageParser<Message> parser = new MessageParser<Message>() {
      @Override public Message parse(ByteBuffer buffer) throws PartialMessageException {
        Assert.fail();
        return null;
      }
    };
    final Callback callback = new Callback();
    final Events events = Events.open();
    Connection<Message> connection = Connection.connect(new InetSocketAddress("localhost", port), parser, callback);
    events.register(connection);
    while (true) {
      if (!events.process(100))
        break;
    }
    server.awaitForStop();
    Assert.assertEquals(callback.total, server.total);
  }

  private final class Callback implements Connection.Callback<Message> {
    private int start;
    private int total;

    @Override public void connected(Connection<Message> connection) {
      Random generator = new Random();

      for (int i = 0; i < 10; i++) {
        List<Message> messages = new ArrayList<Message>();
        for (int j = 0; j < 100; j++) {
          int end = start + generator.nextInt(1024);
          Message message = newMessage(start, end);
          messages.add(message);
          start = end;
        }
        for (Message m : messages) {
          connection.send(m);
          total += m.toByteBuffer().limit();
        }
      }

      connection.close();
    }

    private Message newMessage(int start, int end) {
      byte[] m = new byte[end-start];
      int i = 0;
      for (int j = start; j < end; j++) {
        m[i++] = (byte) (j % 256);
      }
      return new Message(m);
    }

    @Override public void message(Connection<Message> connection, Message message) {
      Assert.fail();
    }

    @Override public void closed(Connection<Message> connection) {
    }

    @Override public void garbledMessage(Connection<Message> connection, String message, byte[] data) {
    }

    @Override public void sent(ByteBuffer buffer) {
    }
  }

  private final class StubServer implements Runnable {
    private final CountDownLatch serverStopped = new CountDownLatch(1);
    private final CountDownLatch serverStarted = new CountDownLatch(1);
    private final int port;
    private int total;
    private boolean closed;

    private StubServer(int port) {
      this.port = port;
    }

    public void awaitForStart() throws InterruptedException {
      serverStarted.await();
    }

    public void awaitForStop() throws InterruptedException {
      serverStopped.await();
    }

    @Override public void run() {
      final MessageParser<Integer> parser = new MessageParser<Integer>() {
        @Override public Integer parse(ByteBuffer buffer) throws PartialMessageException {
          return UnsignedBytes.toInt(buffer.get());
        }
      };
      final Connection.Callback<Integer> callback = new Connection.Callback<Integer>() {
        @Override public void connected(Connection<Integer> connection) {}

        int count = 0;
        @Override public void message(Connection<Integer> connection, Integer message) {
          int ch = message;
          Assert.assertEquals(count++ % 256, ch);
          total++;
        }
        @Override public void closed(Connection<Integer> connection) {
          closed = true;
        }
        @Override public void garbledMessage(Connection<Integer> connection, String garbledMessage, byte[] data) {}
        @Override public void sent(ByteBuffer buffer) {}
      };
      Server server = null;
      try {
        serverStarted.countDown();
        server = Server.accept(port, new Server.ConnectionFactory<Integer>() {
          @Override public Connection<Integer> newConnection(SocketChannel channel) {
            return new Connection(channel, parser, callback);
          }
        });
        Events events = Events.open();
        events.register(server);
        while (!closed)
          events.process(IDLE_MSEC);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        if (server != null) {
          try {
            server.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        serverStopped.countDown();
      }
    }
  }
}
