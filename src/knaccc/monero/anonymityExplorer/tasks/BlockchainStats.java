package knaccc.monero.anonymityExplorer.tasks;

import knaccc.monero.anonymityExplorer.AnonymityExplorer;
import knaccc.monero.anonymityExplorer.Task;
import knaccc.monero.anonymityExplorer.TaskRunner;

import static knaccc.monero.util.TextTableUtil.printFreqMapTable;

public class BlockchainStats implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    AnonymityExplorer.BlockchainStats stats = anonymityExplorer.getBlockchainStats(2133000);
    System.out.println(printFreqMapTable("txPerBlockFreq", stats.txPerBlockFreq, true));
    System.out.println(printFreqMapTable("inputRingMembersPerBlockFreq", stats.inputRingMembersPerBlockFreq, true));
    System.out.println(printFreqMapTable("outputsPerBlockFreq", stats.outputsPerBlockFreq, true));
    System.out.println(printFreqMapTable("outputsPerTxFreq", stats.outputsPerTxFreq, true));
    System.out.println(printFreqMapTable("inputRingMembersPerTxFreq", stats.inputRingMembersPerTxFreq, true));
    System.out.println(printFreqMapTable("inputRingsPerTxFreq", stats.inputRingsPerTxFreq, true));

  }
}
