package knaccc.monero.anonymitySet;

import java.util.HashSet;
import java.util.Set;

public class InputRing {
  public int type;
  public long amount;
  public boolean isRingCt() { return amount==0; }
  public long[] keyOffsets;
  public Set<Long> getRingInputIds() {
    return getRingInputIds(0);
  }
  public Set<Long> getRingInputIds(long minOutputId) {
    Set<Long> result = new HashSet<>();
    long c=0;
    for(long keyOffset : keyOffsets) {
      c+=keyOffset;
      if(c>=minOutputId) result.add(c);
    }
    return result;
  }
}
