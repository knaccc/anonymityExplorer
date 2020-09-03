package knaccc.monero.anonymityExplorer.tasks;

import knaccc.monero.anonymityExplorer.AnonymityExplorer;
import knaccc.monero.anonymityExplorer.Task;
import knaccc.monero.anonymityExplorer.TaskRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static knaccc.monero.util.TextTableUtil.*;

public class OutputRefCounts implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    // todo allow start block to be specified

    int[] outputRefCounts = anonymityExplorer.getOutputRefCounts();

    var table = createEmptyTable();
    table.add(List.of("output refs", "freq", "%"));

    for (int minimumCount = 1; ; minimumCount++) {
      int finalMinimumCount = minimumCount;
      long count = Arrays.stream(outputRefCounts).filter(n->n>=finalMinimumCount).count();
      String percentage = format1dp(100d * (((double) count) / ((double) outputRefCounts.length)));
      List<Object> row = new ArrayList<>();
      row.add(">= " + commaGrouped.format(minimumCount));
      row.add(commaGrouped.format(count));
      row.add(percentage);
      if (count == 0 || (100d * (((double) count) / ((double) outputRefCounts.length))) < 0.1d)
        break; //stop when % below 0.1%
      table.add(row);
    }
    System.out.println(printTable("Output ref counts. Total RingCT outputs: " + commaGrouped.format(outputRefCounts.length), table));


  }
}
