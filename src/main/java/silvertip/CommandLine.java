/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

import jline.Completor;
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
  private Events events;

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

  @Override public SelectionKey register(Events events) throws IOException {
    this.events = events;

    return selectionKey = stdinPipe.getStdinChannel().register(events.selector(), SelectionKey.OP_READ);
  }

  @Override public void read() throws IOException {
    ReadableByteChannel sc = stdinPipe.getStdinChannel();
    ByteBuffer rxBuffer = ByteBuffer.allocate(255);
    if (sc.read(rxBuffer) < 0)
      close();
    rxBuffer.flip();
    callback.commandLine(decoder.decode(rxBuffer).toString());
  }

  @Override public void write() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public EventSource accept() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override public boolean isClosed() {
    return false;
  }

  public void close() {
    if (events != null)
      events.unregister(this);

    selectionKey.attach(null);
    selectionKey.cancel();
    SelectableChannel sc = (SelectableChannel) selectionKey.channel();
    try {
      sc.close();
    } catch (IOException e) {
    }
    selectionKey.selector().wakeup();
  }

  public void setHistory(File historyFile) throws IOException {
    reader.setHistory(new History(historyFile));
  }

  public void addCompletor(Completor completor) {
    reader.addCompletor(completor);
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

    public Pipe.SourceChannel getStdinChannel() throws IOException {
      Pipe.SourceChannel result = pipe.source();
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
