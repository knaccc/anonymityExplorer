package knaccc.monero.anonymityExplorer.tasks;

import knaccc.monero.anonymityExplorer.AnonymityExplorer;
import knaccc.monero.anonymityExplorer.Task;
import knaccc.monero.anonymityExplorer.TaskRunner;
import knaccc.monero.util.Timer;

public class TestOutputCorrelation implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    Timer timer = new Timer();
    int passes=0;
    int tests = 5000;
    for(int i=0; i<tests; i++) {
      timer.printProgress(i, tests);
      long outputId = (long) (Math.random()*((double) anonymityExplorer.getOutputHeight()));
      String txidA = anonymityExplorer.getTxIdForOutputIdViaDaemon(outputId);
      String txidB = anonymityExplorer.lookupTxIdForOutputId(outputId);
      if(txidA.equals(txidB)) passes++;
    }
    System.out.println("passes: " + passes + " of " + tests);

  }
}
