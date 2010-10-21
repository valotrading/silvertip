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
    parse("");
  }

  @Test(expected = PartialMessageException.class)
  public void partialHeader() throws Exception {
    String partialHeader = "8=FIX.4.2" + DELIMITER + "9=153";

    parse(partialHeader);
  }

  @Test public void firstFieldIsNotBeginString() throws Exception {
    String garbled = "X=FIX.4.2" + DELIMITER + "9=5" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void emptyBeginString() throws Exception {
    String garbled = "8=" + DELIMITER + "9=5" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void invalidBeginString() throws Exception {
    String garbled = "8=XIF.10.1" + DELIMITER + "9=5" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void secondFieldIsNotBodyLength() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "X=5" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void emptyBodyLength() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "9=" + DELIMITER + "108=XXX" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void invalidBodyLengthFormat() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "9=XXX" + DELIMITER + "108=XXX" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void thirdFieldIsNotMsgType() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER + "X=A" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void emptyMsgType() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER + "35=" + DELIMITER + "108=XXX" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void missingCheckSum() throws Exception {
    String header = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER;
    String payload = "35=E" + DELIMITER;
    String trailer = "11=XXX" + DELIMITER;
    String message = header + payload + trailer;

    assertGarbledMessage(message);
  }

  @Test public void emptyCheckSum() throws Exception {
    String header = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER;
    String payload = "35=E" + DELIMITER;
    String trailer = "10=" + DELIMITER;
    String message = header + payload + trailer;

    assertGarbledMessage(message);
  }

  @Test public void invalidCheckSumFormat() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER + "35=E" + DELIMITER + "10=XYZ" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void invalidCheckSumLength() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER + "35=E" + DELIMITER + "10=12" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test public void invalidCheckSum() throws Exception {
    String garbled = "8=FIX.4.2" + DELIMITER + "9=5" + DELIMITER + "35=E" + DELIMITER + "10=123" + DELIMITER;

    assertGarbledMessage(garbled);
  }

  @Test(expected = PartialMessageException.class)
  public void partialMessage() throws Exception {
    String header = "8=FIX.4.2" + DELIMITER + "9=153" + DELIMITER + "";

    parse(header);
  }

  @Test
  public void fullMessage() throws Exception {
    String header = "8=FIX.4.2" + DELIMITER + "9=153" + DELIMITER + "";
    String payload = "35=E" + DELIMITER + "49=INST" + DELIMITER + "56=BROK" + DELIMITER + "52=20050908-15:51:22"
        + DELIMITER + "34=200" + DELIMITER + "66=14" + DELIMITER + "394=1" + DELIMITER + "68=2" + DELIMITER + "73=2"
        + DELIMITER + "11=order-1" + DELIMITER + "67=1" + DELIMITER + "55=IBM" + DELIMITER + "54=2" + DELIMITER
        + "38=2000" + DELIMITER + "40=1" + DELIMITER + "11=order-2" + DELIMITER + "67=2" + DELIMITER + "55=AOL"
        + DELIMITER + "54=2" + DELIMITER + "38=1000" + DELIMITER + "40=1" + DELIMITER + "";
    String trailer = "10=020" + DELIMITER;

    Message message = parse(header + payload + trailer);
    Assert.assertEquals(header + payload + trailer, message.toString());
  }

  private void assertGarbledMessage(String garbledMessage) throws Exception {
    assertGarbledMessage(garbledMessage, garbledMessage);
  }

  private void assertGarbledMessage(String messages, String expectedGarbledMessage) throws Exception {
    try {
      parse(messages);
      Assert.fail("GarbledMessageException was not thrown for message: " + expectedGarbledMessage);
    } catch (GarbledMessageException e) {
      Assert.assertEquals(expectedGarbledMessage, new String(e.getMessageData()));
    }
  }

  private Message parse(String message) throws PartialMessageException, GarbledMessageException {
    rxBuffer.put(message.getBytes());
    rxBuffer.flip();
    return parse(rxBuffer);
  }

  private Message parse(ByteBuffer buffer) throws PartialMessageException, GarbledMessageException {
    buffer.mark();
    return parser.parse(buffer);
  }
}
