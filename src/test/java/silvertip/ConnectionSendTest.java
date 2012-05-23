package silvertip;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
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
    Connection<Message> connection = Connection.attemptToConnect(new InetSocketAddress("localhost", port), parser, callback);
    events.register(connection);
    events.dispatch(100);
    server.awaitForStop();
    events.stop();
    Assert.assertEquals(callback.total, server.total);
  }

  private final class Callback implements Connection.Callback<Message> {
    private int count;
    private int start;
    private int total;

    @Override public void idle(Connection<Message> connection) {
      Random generator = new Random();
      List<Message> messages = new ArrayList<Message>();
      for (int i = 0; i < 100; i++) {
        int end = start + generator.nextInt(1024);
        Message message = newMessage(start, end);
        messages.add(message);
        start = end;
      }
      for (Message m : messages) {
        connection.send(m);
        total += m.toByteBuffer().limit();
      }
      if (++count == 10) {
        connection.close();
      }
    }

    private Message newMessage(int start, int end) {
      byte[] m = new byte[end-start];
      int i = 0;
      for (int j = start; j < end; j++) {
        m[i++] = (byte) (j % 256);
      }
      return new Message(m);
    }

    @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
      Assert.fail();
    }

    @Override public void closed(Connection<Message> connection) {
    }

    @Override public void garbledMessage(String message, byte[] data) {
    }
  }

  private final class StubServer implements Runnable {
    private final CountDownLatch serverStopped = new CountDownLatch(1);
    private final CountDownLatch serverStarted = new CountDownLatch(1);
    private final int port;
    private int total;

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
      Connection.Callback<Integer> callback = new Connection.Callback<Integer>() {
        int count = 0;
        @Override public void messages(Connection<Integer> connection, Iterator<Integer> messages) {
          while (messages.hasNext()) {
            int ch = messages.next();
            Assert.assertEquals(count++ % 256, ch);
            total++;
          }
        }
        @Override public void idle(Connection<Integer> connection) {}
        @Override public void closed(Connection<Integer> connection) {}
        @Override public void garbledMessage(String garbledMessage, byte[] data) {}
      };
      Connection<Integer> connection = null;
      try {
        serverStarted.countDown();
        connection = Connection.accept(new InetSocketAddress(port), parser, callback);
        Events events = Events.open();
        events.register(connection);
        events.dispatch(IDLE_MSEC);
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        if (connection != null) {
          connection.close();
        }
        serverStopped.countDown();
      }
    }
  }
}
