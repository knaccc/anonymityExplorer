package knaccc.monero.anonymitySet.tasks;

import knaccc.monero.anonymitySet.AnonymityExplorer;
import knaccc.monero.anonymitySet.Task;
import knaccc.monero.anonymitySet.TaskRunner;
import knaccc.monero.util.Timer;

import java.util.Set;

import static knaccc.monero.anonymitySet.AnonymityExplorer.getRandomIdBetween;

public class DirectMergeProbability implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) {

    // pick two random outputs from July 2020 onwards that are created within 7 days of each other
    // check if those two outputs are ever spent directly together in a tx (via different rings)
    // repeat 1000 times and report success rate

    Timer timer = new Timer();

    int startBlockId = 2133000;
    int latestBlockId = anonymityExplorer.getLastStoredBlockHeight();

    int merges = 0;
    int samplesPerBatch = 100;
    int poisonedIdCount = 2;
    for(int i=0; i<samplesPerBatch; i++) {
      timer.printProgress(i, samplesPerBatch);
      int blockId = (int) getRandomIdBetween(startBlockId, latestBlockId);
      int blockId7DaysLater = blockId + 30*24*7;
      if(blockId7DaysLater>latestBlockId) {
        int d = blockId7DaysLater - latestBlockId;
        blockId-=d;
        blockId7DaysLater-=d;
      }
      long periodStartOutputId = anonymityExplorer.getMinOutputIdForBlockId(blockId);
      long periodEndOutputId = anonymityExplorer.getMinOutputIdForBlockId(blockId7DaysLater);
      long randomOutputIdA = getRandomIdBetween(periodStartOutputId, periodEndOutputId);
      long randomOutputIdB = getRandomIdBetween(periodStartOutputId, periodEndOutputId);

      if(anonymityExplorer.getTxIdsWhereOutputSetsSpentTogether(
        Set.of(Set.of(randomOutputIdA), Set.of(randomOutputIdB))
      ).size()>0) merges++;
    }

    System.out.println("merges detected: " + merges + " of " + samplesPerBatch + " for sets of " + poisonedIdCount + " poisoned outputs");


  }
}
