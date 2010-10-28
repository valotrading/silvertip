package silvertip;

import java.nio.ByteBuffer;
import org.joda.time.DateTime;

public class FixMessage extends Message {
  private String msgType;
  private int msgSeqNum;
  private DateTime receiveTime;

  public static FixMessage fromString(String s, String msgType) {
    return new FixMessage(s.getBytes(), msgType);
  }

  public FixMessage(byte[] data, String msgType) {
    super(data);

    this.msgType = msgType;
  }

  public String getMsgType() {
    return msgType;
  }

  public int getMsgSeqNum() {
    return msgSeqNum;
  }

  public void setMsgSeqNum(int msgSeqNum) {
    this.msgSeqNum = msgSeqNum;
  }

  public DateTime getReceiveTime() {
    return receiveTime;
  }

  public void setReceiveTime(DateTime receiveTime) {
    this.receiveTime = receiveTime;
  }
}
