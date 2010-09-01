package silvertip.samples.pingpong;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import silvertip.Connection;
import silvertip.Events;
import silvertip.Message;
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
      final Events events = Events.open(30000);
      Server server = Server.accept(4444, new ConnectionFactory<Message>() {
        @Override public Connection<Message> newConnection(SocketChannel channel) {
          return new Connection<Message>(channel, new PingPongMessageParser(), new Callback<Message>() {
            private int pingCount;

            @Override public void messages(Connection<Message> connection, Iterator<Message> messages) {
              while (messages.hasNext()) {
                process(connection, messages.next());
              }
            }

            private void process(Connection<Message> connection, Message message) {
              System.out.print("< " + message.toString());
              if ("HELO\n".equals(message.toString()))
                send(connection, Message.fromString("HELO\n"));
              else if ("PING\n".equals(message.toString())) {
                pingCount++;
                if (pingCount < 3)
                  send(connection, Message.fromString("PONG\n"));
                else
                  send(connection, Message.fromString("GBAI\n"));
              }
              else if ("GBAI\n".equals(message.toString()))
                connection.close();
            }

            private void send(Connection<Message> connection, Message message) {
              System.out.print("> " + message.toString());
              connection.send(message);
            }

            @Override public void idle(Connection<Message> connection) {
            }

            @Override public void closed(Connection<Message> connection) {
              events.stop();
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
