package silvertip.samples.console;

import java.io.IOException;

import silvertip.CommandLine;
import silvertip.Events;

public class ConsoleClient {
  public static void main(String[] args) throws IOException {
    CommandLine commandLine = CommandLine.open(new CommandLine.Callback() {
      @Override
      public void commandLine(String commandLine) {
        System.out.println(commandLine);
      }
    });
    Events events = Events.open(30 * 1000);
    events.register(commandLine);
    events.process();
  }
}
