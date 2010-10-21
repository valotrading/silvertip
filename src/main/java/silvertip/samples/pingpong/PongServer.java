package silvertip.samples.pingpong;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import silvertip.Connection;
import silvertip.Events;
import silvertip.Server;
import silvertip.Connection.Callback;
import silvertip.Server.ConnectionFactory;

public class PongServer implements Runnable {
  private final CountDownLatch done = new CountDownLatch(1);

  public static void main(String[] args) {
    PongServer server = new PongServer();
    server.run();
  }

  public void run() {
    try {
      final Events events = Events.open(50);
      Server server = Server.accept(4444, new ConnectionFactory<String>() {
        @Override public Connection<String> newConnection(SocketChannel channel) {
          return new Connection<String>(channel, new PingPongMessageParser(), new Callback<String>() {
            private int pingCount;

            @Override public void messages(Connection<String> connection, Iterator<String> messages) {
              while (messages.hasNext()) {
                process(connection, messages.next());
              }
            }

            private void process(Connection<String> connection, String message) {
              System.out.print("< " + message);
              if ("HELO\n".equals(message))
                send(connection, "HELO\n");
              else if ("PING\n".equals(message)) {
                pingCount++;
                if (pingCount < 3)
                  send(connection, "PONG\n");
                else
                  send(connection, "GBAI\n");
              }
              else if ("GBAI\n".equals(message.toString()))
                connection.close();
            }

            private void send(Connection<String> connection, String message) {
              System.out.print("> " + message);
              connection.send(message.getBytes());
            }

            @Override public void idle(Connection<String> connection) {
            }

            @Override public void closed(Connection<String> connection) {
              events.stop();
            }

            @Override public void garbledMessage(String message, byte[] data) {
            }
          });
        }
      });
      events.register(server);
      done.countDown();
      events.dispatch();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void waitForStartup() {
    try {
      done.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
