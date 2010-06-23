package silvertip.acceptance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import silvertip.samples.pingpong.PingClient;
import silvertip.samples.pingpong.PongServer;

public class AcceptanceTest {
  @Test
  public void clientServer() throws Exception {
    CountDownLatch done = new CountDownLatch(1);
    PongServer server = new PongServer(done);
    Thread serverThread = new Thread(server);
    serverThread.start();

    done.await(10, TimeUnit.SECONDS);

    Thread client = new Thread(new PingClient());
    client.start();

    serverThread.join();
    client.join();
  }
}
