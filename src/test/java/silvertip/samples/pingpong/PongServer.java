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
package silvertip.samples.pingpong;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import silvertip.Connection;
import silvertip.Events;
import silvertip.Server;
import silvertip.Connection.Callback;
import silvertip.Server.ConnectionFactory;

public class PongServer implements Runnable {
  private final CountDownLatch done = new CountDownLatch(1);

  private boolean closed;

  public static void main(String[] args) {
    PongServer server = new PongServer();
    server.run();
  }

  public void run() {
    try {
      final Events events = Events.open();
      Server server = Server.accept(4444, new ConnectionFactory<String>() {
        @Override public Connection<String> newConnection(SocketChannel channel) {
          return new Connection<String>(channel, new PingPongMessageParser(), new Callback<String>() {
            private int pingCount;

            @Override public void connected(Connection<String> connection) {
            }

            @Override public void messages(Connection<String> connection, Iterator<String> messages) {
              while (messages.hasNext()) {
                process(connection, messages.next());
              }
            }

            private void process(Connection<String> connection, String message) {
              System.out.print("< " + message);
              if ("HELO\n".equals(message))
                send(connection, "HELO\n");
              else if ("PING\n".equals(message)) {
                pingCount++;
                if (pingCount < 3)
                  send(connection, "PONG\n");
                else
                  send(connection, "GBAI\n");
              }
              else if ("GBAI\n".equals(message.toString()))
                connection.close();
            }

            private void send(Connection<String> connection, String message) {
              System.out.print("> " + message);
              connection.send(message.getBytes());
            }

            @Override public void closed(Connection<String> connection) {
              closed = true;
            }

            @Override public void garbledMessage(Connection<String> connection, String message, byte[] data) {
            }

            @Override public void sent(ByteBuffer buffer) {
            }
          });
        }
      });
      events.register(server);
      done.countDown();
      while (!closed) {
        if (!events.process(50))
          break;
      }
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
