package knaccc.monero.util;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ByteUtil {

   public static byte[] hexToBytes(String s) {
       int len = s.length();
       byte[] data = new byte[len / 2];
       for (int i = 0; i < len; i += 2) {
           data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                + Character.digit(s.charAt(i+1), 16));
       }
       return data;
   }

  public static byte[] subarray(byte[] a, int start, int end) {
    byte[] r = new byte[end-start];
    System.arraycopy(a, start, r, 0, r.length);
    return r;
  }

  public static byte[] longToUint32Bytes(long value) {
    byte[] bytes = new byte[8];
    ByteBuffer.wrap(bytes).putLong(value);
    return Arrays.copyOfRange(bytes, 4, 8);
  }
  public static long uint32BytesToLong(byte[] bytes) {
     ByteBuffer buffer = ByteBuffer.allocate(8).put(new byte[]{0, 0, 0, 0}).put(bytes);
     buffer.position(0);
     return buffer.getLong();
   }


}
