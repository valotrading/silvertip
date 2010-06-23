package silvertip.protocols;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

import silvertip.GarbledMessageException;
import silvertip.Message;
import silvertip.PartialMessageException;

public class FixMessageParserTest {
  private static final int RECEIVE_BUFFER_SIZE = 4096;
  private static final char DELIMITER = '\001';

  private final ByteBuffer rxBuffer = ByteBuffer.allocate(RECEIVE_BUFFER_SIZE);
  private FixMessageParser parser = new FixMessageParser();

  @Test(expected = PartialMessageException.class)
  public void empty() throws Exception {
    rxBuffer.flip();
    parse();
  }

  @Test(expected = PartialMessageException.class)
  public void partialHeader() throws Exception {
    String partialHeader = "8=FIX.4.2" + DELIMITER + "9=153";
    rxBuffer.put(partialHeader.getBytes());
    rxBuffer.flip();
    parse();
  }

  @Test(expected = GarbledMessageException.class)
  public void garbledBeginString() throws Exception {
    String garbled = "X=FIX.4.2" + DELIMITER + "9=5" + DELIMITER;

    rxBuffer.put(garbled.getBytes());
    rxBuffer.flip();

    parse();
  }

  @Test(expected = GarbledMessageException.class)
  public void garbledBodyLength() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "X=5" + DELIMITER;

    rxBuffer.put(garbled.getBytes());
    rxBuffer.flip();

    parse();
  }

  @Test(expected = GarbledMessageException.class)
  public void garbledCheckSum() throws Exception {
    String header = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER;
    String payload = "35=E" + DELIMITER;
    String trailer = "11=XXX" + DELIMITER;

    rxBuffer.put((header + payload + trailer).getBytes());
    rxBuffer.flip();

    parse();
  }

  @Test
  public void fullMessage() throws Exception {
    String header = "8=FIX.4.2" + DELIMITER + "9=153" + DELIMITER + "";
    String payload = "35=E" + DELIMITER + "49=INST" + DELIMITER + "56=BROK" + DELIMITER + "52=20050908-15:51:22"
        + DELIMITER + "34=200" + DELIMITER + "66=14" + DELIMITER + "394=1" + DELIMITER + "68=2" + DELIMITER + "73=2"
        + DELIMITER + "11=order-1" + DELIMITER + "67=1" + DELIMITER + "55=IBM" + DELIMITER + "54=2" + DELIMITER
        + "38=2000" + DELIMITER + "40=1" + DELIMITER + "11=order-2" + DELIMITER + "67=2" + DELIMITER + "55=AOL"
        + DELIMITER + "54=2" + DELIMITER + "38=1000" + DELIMITER + "40=1" + DELIMITER + "";
    String trailer = "10=XXX" + DELIMITER + "";

    rxBuffer.put((header + payload + trailer).getBytes());
    rxBuffer.flip();

    Message message = parse();
    Assert.assertEquals(header + payload + trailer, message.toString());
  }

  private Message parse() throws PartialMessageException, GarbledMessageException {
    return parser.parse(rxBuffer);
  }
}
