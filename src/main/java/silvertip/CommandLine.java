package silvertip;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import jline.ConsoleReader;
import jline.History;

public class CommandLine implements EventSource {
  private static final String PROMPT = "> ";
  private static final Charset charset = Charset.forName("UTF-8");
  private static final CharsetDecoder decoder = charset.newDecoder();

  public interface Callback {
    void commandLine(String commandLine);
  }

  private SelectionKey selectionKey;
  private final Callback callback;
  private SystemInPipe stdinPipe;
  private static ConsoleReader reader;

  public static CommandLine open(Callback callback) throws IOException {
    reader = new ConsoleReader();
    return open(new Console() {
      @Override public void println(String string) throws IOException {
        reader.putString(string);
      }

      @Override public String readLine(String prompt) throws IOException {
        return reader.readLine(prompt);
      }
    }, callback);
  }

  public static CommandLine open(Console console, Callback callback) throws IOException {
    SystemInPipe stdinPipe = new SystemInPipe(console);
    CommandLine result = new CommandLine(callback, stdinPipe);
    stdinPipe.start(result);
    return result;
  }

  public CommandLine(Callback callback, SystemInPipe stdinPipe) {
    this.callback = callback;
    this.stdinPipe = stdinPipe;
  }

  public SelectionKey register(Selector selector, int ops) throws IOException {
    return selectionKey = getChannel().register(selector, ops);
  }

  @Override public void unregister() {
  }

  private SelectableChannel getChannel() {
    try {
      return stdinPipe.getStdinChannel();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override public void read(SelectionKey key) throws IOException {
    ReadableByteChannel sc = (ReadableByteChannel) key.channel();
    ByteBuffer rxBuffer = ByteBuffer.allocate(255);
    if (sc.read(rxBuffer) < 0)
      close();
    rxBuffer.flip();
    callback.commandLine(decoder.decode(rxBuffer).toString());
  }

  @Override public void write(SelectionKey key) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public EventSource accept(SelectionKey key) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public void timeout() {
  }

  public void close() {
    selectionKey.attach(null);
    selectionKey.cancel();
    SelectableChannel sc = (SelectableChannel) selectionKey.channel();
    try {
      sc.close();
    } catch (IOException e) {
    }
    selectionKey.selector().wakeup();
  }

  @Override public boolean isClosed() {
    return false;
  }

  public void setHistory(File historyFile) throws IOException {
    reader.setHistory(new History(historyFile));
  }

  private static class SystemInPipe {
    private CopyThread copyThread;
    private Console console;
    private Pipe pipe;

    public SystemInPipe(Console console) throws IOException {
      this.console = console;
      pipe = Pipe.open();
    }

    public void start(CommandLine commandLine) throws IOException {
      copyThread = new CopyThread(commandLine, console, pipe.sink());
      copyThread.start();
    }

    public SelectableChannel getStdinChannel() throws IOException {
      SelectableChannel result = pipe.source();
      result.configureBlocking(false);
      return result;
    }

    private class CopyThread extends Thread {
      private final CommandLine commandLine;
      private final WritableByteChannel out;
      private final Console console;

      CopyThread(CommandLine commandLine, Console console, WritableByteChannel out) throws IOException {
        this.commandLine = commandLine;
        this.console = console;
        this.out = out;
        setDaemon(true);
      }

      public void run() {
        try {
          for (;;) {
            String line = console.readLine(PROMPT);
            if (line == null)
              break;
            ByteBuffer buffer = ByteBuffer.wrap(line.getBytes());
            out.write(buffer);
          }
          out.close();
          commandLine.close();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
