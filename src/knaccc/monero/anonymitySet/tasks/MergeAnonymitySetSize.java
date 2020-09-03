package knaccc.monero.anonymitySet.tasks;

import knaccc.monero.anonymitySet.AnonymityExplorer;
import knaccc.monero.anonymitySet.Task;
import knaccc.monero.anonymitySet.TaskRunner;
import knaccc.monero.util.Timer;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static knaccc.monero.anonymitySet.AnonymityExplorer.getRandomIdBetween;
import static knaccc.monero.util.TextTableUtil.createEmptyTable;
import static knaccc.monero.util.TextTableUtil.printTable;

public class MergeAnonymitySetSize implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    // key question: if you send me two poisoned outputs, and I merge them, how many other ways will the blockchain
    //   also report that they might have been merged?

    // pick a random outputs on the blockchain created during month of July 2020
    // count the number of direct or indirect merges observed

    // repeat for several pairs of two random poisoned outputs

    Timer timer = new Timer();

    int startBlockId = 2133000; // approx. Jul 2020 onwards
    int endBlockId = startBlockId + 30*24*30; // 1 month later

    long periodStartOutputId = anonymityExplorer.getMinOutputIdForBlockId(startBlockId);
    long periodEndOutputId = anonymityExplorer.getMinOutputIdForBlockId(endBlockId);

    List.of(2, 4, 24, 48, 96).forEach(churnWindowHours->{
      int maxChurnBlockDistance = 30 * churnWindowHours; // constrain churn distance (30 blocks = 1 hour)

      var table = createEmptyTable();
      table.add(List.of("Direct", "Level 1",  "Level 2",  "Level 3",  "Level 4"));

      int iterations = 5;
      for (int i = 0; i < iterations; i++) {

        timer.printProgress(i, iterations);

        long outputIdA = getRandomIdBetween(periodStartOutputId, periodEndOutputId);
        long outputIdB = getRandomIdBetween(periodStartOutputId, periodEndOutputId);

        int maxLevels = 4;
        int[] merges = new int[maxLevels + 1];
        for (int levels = 0; levels <= maxLevels; levels++) {
          int finalLevels = levels;
          merges[levels] += anonymityExplorer.getTxIdsWhereOutputSetsSpentTogether(
            Set.of(outputIdA, outputIdB).stream().map(id -> anonymityExplorer.getForwardOutputIdRefs(id, finalLevels, maxChurnBlockDistance)).collect(Collectors.toSet())).size();
        }
        System.out.println("possible merges detected: " + Arrays.toString(merges));
        table.add(Arrays.stream(merges).mapToObj(n->n+"").collect(Collectors.toList()));
      }
      System.out.println(printTable("Possible merges of 2 random outputs, detected with churn window = " + churnWindowHours + " hours", table));

    });


  }
}
