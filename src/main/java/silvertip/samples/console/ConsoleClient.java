package silvertip.samples.console;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;

import silvertip.CommandLine;
import silvertip.Connection;
import silvertip.Events;
import silvertip.samples.pingpong.PingPongMessageParser;

public class ConsoleClient {
  public static void main(String[] args) throws IOException {
    String hostname = "localhost";
    int port = 4444;

    final Events events = Events.open();

    final Connection<String> connection = Connection.connect(new InetSocketAddress(hostname, port),
        new PingPongMessageParser(), new Connection.Callback<String>() {
          @Override public void connected(Connection<String> connection) {
          }

          @Override public void messages(Connection<String> connection, Iterator<String> messages) {
            while (messages.hasNext()) {
              String m = messages.next();
              if ("GBAI\n".equals(m)) {
                connection.send("GBAI\n".getBytes());
                events.stop();
              }
            }
          }

          @Override public void idle(Connection<String> connection) {
            System.out.println("Idle detected.");
          }

          @Override public void closed(Connection<String> connection) {
          }

          @Override public void garbledMessage(Connection<String> connection, String message, byte[] data) {
          }
        });
    final CommandLine commandLine = CommandLine.open(new CommandLine.Callback() {
      @Override public void commandLine(String commandLine) {
        connection.send((commandLine + "\n").getBytes());
      }
    });

    events.register(commandLine);
    events.register(connection);
    events.dispatch(30 * 1000);
  }
}
