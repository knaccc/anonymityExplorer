package knaccc.monero.anonymitySet;

import java.util.List;

public class Transaction {
  public List<InputRing> inputRings;
  public int outputCount;
  public int version;
  public int blockId;
  public String id;

  public boolean isRingCt() { return version>=2; }

}
