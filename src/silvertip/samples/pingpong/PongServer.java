package silvertip.samples.pingpong;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;

public class PongServer implements Runnable {
  private final CountDownLatch done;
  private BufferedReader in;
  private PrintWriter out;
  private Socket clientSocket;

  public PongServer(CountDownLatch done) {
    this.done = done;
  }

  public void run() {
    try {
      setup();
      waitAndSend("HELO", "HELO");
      waitAndSend("PING", "PONG");
      waitAndSend("PING", "PONG");
      waitAndSend("PING", "PONG");
      sendAndWait("GBAI", "GBAI");
      clientSocket.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void setup() throws IOException {
    ServerSocket serverSocket = new ServerSocket(4444);
    done.countDown();
    clientSocket = serverSocket.accept();
    out = new PrintWriter(clientSocket.getOutputStream(), true);
    in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
  }

  private void waitAndSend(String expectedRequest, String response) throws IOException {
    String request = receive();
    if (!request.equals(expectedRequest))
      throw new AssertionError("Expected: " + expectedRequest + ", but was: " + request);
    send(response);
  }

  private void sendAndWait(String request, String expectedResponse) throws IOException {
    send(request);
    String response = receive();
    if (!response.equals(expectedResponse))
      throw new AssertionError("Expected: " + expectedResponse + ", but was: " + response);
  }

  private String receive() throws IOException {
    String message = in.readLine();
    System.out.println("< " + message);
    return message;
  }

  private void send(String message) {
    out.println(message);
    System.out.println("> " + message);
  }
}
