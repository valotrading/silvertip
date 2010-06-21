package silvertip;

import java.nio.ByteBuffer;

public interface MessageParser {
  Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException;
}