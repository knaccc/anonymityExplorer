package knaccc.monero.anonymityExplorer.tasks;

import knaccc.monero.anonymityExplorer.AnonymityExplorer;
import knaccc.monero.anonymityExplorer.Task;
import knaccc.monero.anonymityExplorer.TaskRunner;
import knaccc.monero.util.Timer;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static knaccc.monero.anonymityExplorer.AnonymityExplorer.getRandomIdBetween;

public class Tx2OutMergeProbability implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    // pick a random 2-out tx from the blockchain during month of July 2020
    // search for direct or indirect merges
    // repeat n times, report how many times merges detected

    Timer timer = new Timer();

    int maxChurnBlockDistance = 30 * 24 * 365;

    int startBlockId = 2133000; // approx. Jul 2020 onwards
    int endBlockId = startBlockId + 30*24*30; // 1 month later

    long startOutputId = anonymityExplorer.getMinOutputIdForBlockId(startBlockId);
    long endOutputId = anonymityExplorer.getMinOutputIdForBlockId(endBlockId);

    int levelsMax = 2;
    int[] merges = new int[levelsMax+1];

    int iterations = 10;
    for (int i = 0; i < iterations; i++) {

      timer.printProgress(i, iterations);
      long randomOutputId = getRandomIdBetween(startOutputId, endOutputId);
      AnonymityExplorer.TxRecord txRecord = anonymityExplorer.getTxRecord(randomOutputId);
      if (txRecord.outputCount != 2) {
        i--;
        continue;
        // try again to locate a tx with exactly 2 outputs
      }
      long outputIdA = txRecord.outputIdStart;
      long outputIdB = txRecord.outputIdStart + 1;

      for(int levels=0; levels<=levelsMax; levels++) {
        final int finalLevels = levels;
        if (anonymityExplorer.getTxIdsWhereOutputSetsSpentTogether(
          Set.of(outputIdA, outputIdB).stream().map(id -> anonymityExplorer.getForwardOutputIdRefs(id, finalLevels, maxChurnBlockDistance)).collect(Collectors.toSet())
        ).size() > 0) merges[levels]++;
      }

    }
    System.out.println();
    System.out.println("merges: " + Arrays.toString(merges) + " of " + iterations + " iterations");

  }
}
