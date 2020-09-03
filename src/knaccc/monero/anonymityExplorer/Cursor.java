package knaccc.monero.anonymityExplorer;

import java.nio.ByteBuffer;

import static knaccc.monero.util.ByteUtil.subarray;

public class Cursor {

  public int pos = 0;
  protected byte[] data;
  public Cursor(byte[] data) {
    this.data = data;
  }

  public static byte[] toVarInt(long v) {
    if(v<0x80) return new byte[]{(byte)v};

    byte[] buf = new byte[9];
    int i=0;
    while (v >= 0x80) {
      buf[i++] = (byte) (v | 0x80);
      v >>= 7;
    }
    buf[i++] = (byte)(v);
    return subarray(buf, 0, i);
  }

  public static void main(String[] args) {
    for(long i=0; i<10*1000*1000; i++) {
      if(readVarInt(toVarInt(i), 0)!=i) throw new RuntimeException("fail");
    }
  }

  public long readVarInt() {
    long result = 0;
    int c = 0;
    while(true) {
     boolean isLastByteInVarInt = true;
     int i = Byte.toUnsignedInt(data[pos]);
     if(i>=128) {
       isLastByteInVarInt = false;
       i-=128;
     }
     result += Math.round(i * Math.pow(128, c));
     c++;
     pos++;
     if(isLastByteInVarInt) break;
    }
    return result;
  }
  public static long readVarInt(ByteBuffer buf) {
    long result = 0;
    int c = 0;
    while(true) {
      boolean isLastByteInVarInt = true;
      int i = Byte.toUnsignedInt(buf.get());
      if(i>=128) {
        isLastByteInVarInt = false;
        i-=128;
      }
      result += Math.round(i * Math.pow(128, c));
      c++;
      if(isLastByteInVarInt) break;
    }
    return result;
  }
  public static long readVarInt(byte[] data, int pos) {
    long result = 0;
    int c = 0;
    while(true) {
      boolean isLastByteInVarInt = true;
      int i = Byte.toUnsignedInt(data[pos]);
      if(i>=128) {
        isLastByteInVarInt = false;
        i-=128;
      }
      result += Math.round(i * Math.pow(128, c));
      c++;
      pos++;
      if(isLastByteInVarInt) break;
    }
    return result;
  }

  public byte[] readBytes(int len) {
    byte[] bytes = new byte[len];
    System.arraycopy(data, pos, bytes, 0, len);
    pos+=len;
    return bytes;
  }

  public int readByte() {
    pos++;
    return Byte.toUnsignedInt(data[pos-1]);
  }

  public byte[] readKey() {
    return readBytes(32);
  }


}
