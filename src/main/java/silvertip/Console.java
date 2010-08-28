package silvertip;

import java.io.IOException;

public interface Console {
  void println(String string) throws IOException;
  String readLine(String prompt) throws IOException;
}
