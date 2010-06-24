package silvertip.acceptance;

import org.junit.Test;

import silvertip.samples.pingpong.PingClient;
import silvertip.samples.pingpong.PongServer;

public class AcceptanceTest {
  @Test
  public void clientServer() throws Exception {
    PongServer server = new PongServer();
    Thread serverThread = new Thread(server);
    serverThread.start();

    server.waitForStartup();

    Thread client = new Thread(new PingClient());
    client.start();

    serverThread.join();
    client.join();
  }
}
