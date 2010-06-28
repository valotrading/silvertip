package silvertip;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

public class CommandLine implements EventSource {
  private static final Charset charset = Charset.forName("UTF-8");
  private static final CharsetDecoder decoder = charset.newDecoder();

  public interface Callback {
    void commandLine(String commandLine);
  }

  private SelectionKey selectionKey;
  private final Callback callback;
  private SystemInPipe stdinPipe;

  public static CommandLine open(Callback callback) throws IOException {
    SystemInPipe stdinPipe = new SystemInPipe();
    CommandLine result = new CommandLine(callback, stdinPipe);
    stdinPipe.start(result);
    return result;
  }

  public CommandLine(Callback callback, SystemInPipe stdinPipe) {
    this.callback = callback;
    this.stdinPipe = stdinPipe;
  }

  @Override
  public SelectableChannel getChannel() {
    try {
      return stdinPipe.getStdinChannel();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void read(SelectionKey key) throws IOException {
    ReadableByteChannel sc = (ReadableByteChannel) key.channel();
    ByteBuffer rxBuffer = ByteBuffer.allocate(255);
    if (sc.read(rxBuffer) < 0)
      close();
    rxBuffer.flip();
    callback.commandLine(decoder.decode(rxBuffer).toString());
  }

  @Override
  public void setSelectionKey(SelectionKey selectionKey) {
    this.selectionKey = selectionKey;
  }

  @Override
  public void timeout() {
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

  private static class SystemInPipe {
    private CopyThread copyThread;
    private InputStream in;
    private Pipe pipe;

    public SystemInPipe() throws IOException {
      this(System.in);
    }

    public SystemInPipe(InputStream in) throws IOException {
      this.in = in;
      pipe = Pipe.open();
    }

    public void start(CommandLine commandLine) {
      copyThread = new CopyThread(commandLine, in, pipe.sink());
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
      private final BufferedReader in;

      CopyThread(CommandLine commandLine, InputStream in, WritableByteChannel out) {
        this.commandLine = commandLine;
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = out;
        setDaemon(true);
      }

      public void run() {
        try {
          for (;;) {
            String line = in.readLine();
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
