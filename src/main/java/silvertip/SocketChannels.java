package silvertip;

import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;

abstract class SocketChannels {

  public static void close(SocketChannel channel) {
    Socket socket = channel.socket();
    if (socket != null) {
      try {
        socket.shutdownInput();
      } catch (IOException e) {
      }
      try {
        socket.shutdownOutput();
      } catch (IOException e) {
      }
      try {
        socket.close();
      } catch (IOException e) {
      }
    }

    try {
      channel.close();
    } catch (IOException e) {
    }
  }

}
