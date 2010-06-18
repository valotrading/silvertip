package silvertip.samples.pingpong;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;

import silvertip.Callback;
import silvertip.Connection;
import silvertip.Message;

public class PingClient implements Runnable {
  @Override
  public void run() {
    String hostname = "localhost";
    int port = 4444;

    try {
      final Connection connection = Connection.connect(new InetSocketAddress(hostname, port), 100);

      connection.send(Message.fromString("HELO\n"));
      connection.wait(new Callback() {
        public void messages(Iterator<Message> messages) {
          while (messages.hasNext()) {
            Message m = messages.next();
            if (m.toString().equals("GBAI\n")) {
              connection.send(Message.fromString("GBAI\n"));
            }
          }
        }

        public void idle() {
          connection.send(Message.fromString("PING\n"));
        }
      });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}