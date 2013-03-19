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
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Iterator;

import silvertip.Connection;
import silvertip.Events;

public class PingClient implements Runnable {
  @Override public void run() {
    String hostname = "localhost";
    int port = 4444;

    try {
      final Connection<String> connection = Connection.connect(new InetSocketAddress(hostname, port),
          new PingPongMessageParser(), new Connection.Callback<String>() {
            @Override public void connected(Connection<String> connection) {
              connection.send("PING\n".getBytes());
            }

            @Override public void messages(Connection<String> connection, Iterator<String> messages) {
              while (messages.hasNext()) {
                String m = messages.next();
                if ("PONG\n".equals(m)) {
                  connection.send("PING\n".getBytes());
                }

                if ("GBAI\n".equals(m)) {
                  connection.send("GBAI\n".getBytes());
                }
              }
            }

            @Override public void closed(Connection<String> connection) {
            }

            @Override public void garbledMessage(Connection<String> connection, String message, byte[] data) {
            }

            @Override public void sent(ByteBuffer buffer) {
            }
          });
      Events events = Events.open();
      events.register(connection);
      connection.send("HELO\n".getBytes());
      while (true) {
        if (!events.process(100))
          break;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
