package silvertip.samples.console;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;

import silvertip.CommandLine;
import silvertip.Connection;
import silvertip.Events;
import silvertip.Message;
import silvertip.samples.pingpong.PingPongMessageParser;

public class ConsoleClient {
  public static void main(String[] args) throws IOException {
    String hostname = "localhost";
    int port = 4444;

    final Events events = Events.open(30 * 1000);

    final Connection connection = Connection.connect(new InetSocketAddress(hostname, port),
        new PingPongMessageParser(), new Connection.Callback() {
          public void messages(Connection connection, Iterator<Message> messages) {
            while (messages.hasNext()) {
              Message m = messages.next();
              if (m.toString().equals("GBAI\n")) {
                connection.send(Message.fromString("GBAI\n"));
                events.stop();
              }
            }
          }

          public void idle(Connection connection) {
            System.out.println("Idle detected.");
          }

          @Override public void closed(Connection connection) {
          }
        });
    final CommandLine commandLine = CommandLine.open(new CommandLine.Callback() {
      @Override
      public void commandLine(String commandLine) {
        connection.send(Message.fromString(commandLine + "\n"));
      }
    });

    events.register(commandLine);
    events.register(connection);
    events.dispatch();
  }
}
