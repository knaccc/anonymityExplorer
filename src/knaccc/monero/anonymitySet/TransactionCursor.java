package knaccc.monero.anonymitySet;

import java.util.ArrayList;
import java.util.List;

public class TransactionCursor extends Cursor {

  public TransactionCursor(byte[] data) {
    super(data);
  }

  public List<InputRing> readInputs() {
    List<InputRing> inputRings = new ArrayList<>();
    int input_num = (int) readVarInt();
    for (int i = 0; i < input_num; i++) {
      InputRing inputRing = new InputRing();
      inputRing.type = readByte();
      inputRing.amount = readVarInt();
      int keyOffsetNum = (int) readVarInt();
      inputRing.keyOffsets = new long[keyOffsetNum];
      for (int j = 0; j < keyOffsetNum; j++) inputRing.keyOffsets[j] = readVarInt();
      readKey(); // skip over key image
      inputRings.add(inputRing);
    }
    return inputRings;
  }

}
