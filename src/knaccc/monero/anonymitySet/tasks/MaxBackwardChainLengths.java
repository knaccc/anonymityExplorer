package knaccc.monero.anonymitySet.tasks;

import knaccc.monero.anonymitySet.AnonymityExplorer;
import knaccc.monero.anonymitySet.Task;
import knaccc.monero.anonymitySet.TaskRunner;

import java.util.*;

import static knaccc.monero.util.TextTableUtil.*;

public class MaxBackwardChainLengths implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    var table = createEmptyTable();
    table.add(List.of("backward chain length", "freq", "%"));

    int[] maxBackwardChainLengths = anonymityExplorer.getMaxBackwardChainLengths();
    Map<Integer, Integer> backwardChainLengthCount = new HashMap<>();
    for (int i : maxBackwardChainLengths) {
      backwardChainLengthCount.putIfAbsent(i, 0);
      backwardChainLengthCount.computeIfPresent(i, (i2, v) -> v + 1);
    }
    int maxBackwardChainLen = backwardChainLengthCount.values().stream().mapToInt(v -> v).max().getAsInt();
    String lastPc="";
    for (int i = 1; i < maxBackwardChainLen; i+=(i>=1000) ? 1000 : 1) {
      int finalI = i;
      int count = backwardChainLengthCount.entrySet().stream().filter(e -> e.getKey() >= finalI).mapToInt(Map.Entry::getValue).sum();
      String percentage = format1dp(100d * (((double) count) / ((double) maxBackwardChainLengths.length)));
      if(count==0) break;
      if (lastPc.equals(percentage) && i>10) continue;
      lastPc = percentage;
      List<Object> row = new ArrayList<>();
      row.add(">= " + commaGrouped.format(i));
      row.add(commaGrouped.format(count));
      row.add(percentage);
      table.add(row);
    }
    System.out.println(printTable("Max backward chain lengths. Total RingCT outputs: " + commaGrouped.format(maxBackwardChainLengths.length), table));

  }
}
