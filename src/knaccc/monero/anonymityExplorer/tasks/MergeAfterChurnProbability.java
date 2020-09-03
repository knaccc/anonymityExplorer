package knaccc.monero.anonymityExplorer.tasks;

import knaccc.monero.anonymityExplorer.AnonymityExplorer;
import knaccc.monero.anonymityExplorer.Task;
import knaccc.monero.anonymityExplorer.TaskRunner;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static knaccc.monero.anonymityExplorer.AnonymityExplorer.getRandomIdBetween;
import static knaccc.monero.anonymityExplorer.AnonymityExplorer.getRandomIdSetBetween;
import static knaccc.monero.util.TextTableUtil.*;

public class MergeAfterChurnProbability implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) {

    // pick n random outputs from July 2020 onwards that are within 7 days of each other
    // get the {churn}-level forward anonymity output set for each
    // check if any txs exist where one output member out of every forward anonymity output set are spent together (i.e. merged)
    // repeat 100 times and report success rate

    // todo this isn't useful when n>2, since we need to account for situations where the merge tx is not a single tx
    //  that merges all 3 forward anonymity sets at once

    int startBlockId = 2133000;
    int latestBlockId = anonymityExplorer.getLastStoredBlockHeight();

    int maxChurnBlockDistance = 30*24*1;

    var table = createEmptyTable();
    table.add(List.of("Churn level", "Poisoned output count", "Merges detected"));

    for(int churnLevel = 0; churnLevel<=3; churnLevel++) {
      int finalChurnLevel = churnLevel;
      int samplesPerBatch = 10;
      for (int poisonedIdCount = 2; poisonedIdCount <=4; poisonedIdCount++) {
        int merges = 0;
        for (int i = 0; i <samplesPerBatch; i++) {
          int blockId = (int) getRandomIdBetween(startBlockId, latestBlockId);
          int blockId7DaysLater = blockId + 30 * 24 * 7;
          if (blockId7DaysLater > latestBlockId) {
            int d = blockId7DaysLater - latestBlockId;
            blockId -= d;
            blockId7DaysLater -= d;
          }
          long periodStartOutputId = anonymityExplorer.getMinOutputIdForBlockId(blockId);
          long periodEndOutputId = anonymityExplorer.getMinOutputIdForBlockId(blockId7DaysLater);
          Set<Long> randomOutputIdsInPeriod = getRandomIdSetBetween(poisonedIdCount, periodStartOutputId, periodEndOutputId);

          if (anonymityExplorer.getTxIdsWhereOutputSetsSpentTogether(
            randomOutputIdsInPeriod.stream().map(id -> anonymityExplorer.getForwardOutputIdRefs(id, finalChurnLevel, maxChurnBlockDistance)).collect(Collectors.toSet())
          ).size() > 0) merges++;
        }
        System.out.println("merges detected: " + merges + " of " + samplesPerBatch + " for " + poisonedIdCount + " poisoned outputs at churn level: " + churnLevel);
        table.add(List.of(churnLevel+"", poisonedIdCount+"", format0dp((100d*merges)/((double) samplesPerBatch))+"%"));
      }
    }
    System.out.println(printTable("Merge after churn probabilities, churn window (hours):" + (maxChurnBlockDistance / 30), table));
  }
}
