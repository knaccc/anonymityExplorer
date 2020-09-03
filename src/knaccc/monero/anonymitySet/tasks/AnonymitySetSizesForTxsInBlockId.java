package knaccc.monero.anonymitySet.tasks;

import knaccc.monero.anonymitySet.AnonymityExplorer;
import knaccc.monero.anonymitySet.Task;
import knaccc.monero.anonymitySet.TaskRunner;

import java.util.ArrayList;
import java.util.List;

import static knaccc.monero.util.TextTableUtil.createEmptyTable;
import static knaccc.monero.util.TextTableUtil.printTable;

public class AnonymitySetSizesForTxsInBlockId implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    int blockId = 2177013;
    int windowDays = 7;
    var table = createEmptyTable();
    List<Object> headerRow = new ArrayList<>();
    int levels = 3;
    headerRow.add("txid");
    table.add(headerRow);
    for (int i = 1; i <= levels; i++) headerRow.add("Level=" + i);
    anonymityExplorer.daemonRpc.getTransactionsForTxIds(anonymityExplorer.daemonRpc.getBlock(blockId).getNonCoinbaseTxIds()).forEach(t->{
      List<Object> row = new ArrayList<>();
      row.add(t.id);
      anonymityExplorer.getAnonymitySetForTxId(t.id, levels, windowDays).forEach(i->row.add(i));
      table.add(row);
    });
    System.out.println(printTable("Anonymity set sizes for txs in block " + blockId + " for observation window size: " + (windowDays==0?"All":(windowDays+ " days")), table));


  }
}
