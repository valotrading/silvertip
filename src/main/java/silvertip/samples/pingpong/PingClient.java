package silvertip.samples.pingpong;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;

import silvertip.Connection;
import silvertip.Events;

public class PingClient implements Runnable {
  @Override
  public void run() {
    String hostname = "localhost";
    int port = 4444;

    try {
      final Connection<String> connection = Connection.connect(new InetSocketAddress(hostname, port),
          new PingPongMessageParser(), new Connection.Callback<String>() {
            public void messages(Connection<String> connection, Iterator<String> messages) {
              while (messages.hasNext()) {
                String m = messages.next();
                if ("GBAI\n".equals(m)) {
                  connection.send("GBAI\n".getBytes());
                }
              }
            }

            public void idle(Connection<String> connection) {
              connection.send("PING\n".getBytes());
            }

            @Override public void closed(Connection<String> connection) {
            }

            @Override public void garbledMessage(String message, byte[] data) {
            }
          });
      Events events = Events.open(100);
      events.register(connection);
      connection.send("HELO\n".getBytes());
      events.dispatch();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
