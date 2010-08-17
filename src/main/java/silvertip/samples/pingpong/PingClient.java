package silvertip.samples.pingpong;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;

import silvertip.Connection;
import silvertip.Events;
import silvertip.Message;

public class PingClient implements Runnable {
  @Override public void run() {
    String hostname = "localhost";
    int port = 4444;

    try {
      final Connection connection = Connection.connect(new InetSocketAddress(hostname, port),
          new PingPongMessageParser(), new Connection.Callback() {
            public void messages(Connection connection, Iterator<Message> messages) {
              while (messages.hasNext()) {
                Message m = messages.next();
                if (m.toString().equals("GBAI\n")) {
                  connection.send(Message.fromString("GBAI\n"));
                }
              }
            }

            public void idle(Connection connection) {
              connection.send(Message.fromString("PING\n"));
            }
          });

      Events events = Events.open(100);
      events.register(connection);
      connection.send(Message.fromString("HELO\n"));
      events.dispatch();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}