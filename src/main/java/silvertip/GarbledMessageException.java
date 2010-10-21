package silvertip;

public class GarbledMessageException extends Exception {
  private static final long serialVersionUID = 1L;

  private byte[] messageData;

  public byte[] getMessageData() {
    return messageData;
  }

  public void setMessageData(byte[] messageData) {
    this.messageData = messageData;
  }
}
