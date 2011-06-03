package silvertip;

public class UnsignedBytes {
  public static int toInt(byte value) {
    return value & 0xff;
  }
}
