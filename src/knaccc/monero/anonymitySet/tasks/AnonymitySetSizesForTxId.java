package knaccc.monero.anonymitySet.tasks;

import knaccc.monero.anonymitySet.AnonymityExplorer;
import knaccc.monero.anonymitySet.Task;
import knaccc.monero.anonymitySet.TaskRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static knaccc.monero.util.TextTableUtil.createEmptyTable;
import static knaccc.monero.util.TextTableUtil.printTable;

public class AnonymitySetSizesForTxId implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    String txid = "abff2ab014df0c38fb4a36770d67e0c8944ef516cab7d3e2550b8cc14dafa9a8";
    var table = createEmptyTable();
    List<Object> headerRow = new ArrayList<>();
    int levels = 5;
    headerRow.add("Window (days)");
    for (int i = 1; i <= levels; i++) headerRow.add("Level=" + i);
    table.add(headerRow);
    for (int windowDays : Arrays.asList(1, 3, 7, 14, 30, 90, 365, 0)) {
    //for (int windowDays : Arrays.asList(1, 3, 7, 14, 30)) {
      List<Object> row = new ArrayList<>();
      row.add(windowDays == 0 ? "All" : windowDays);
      anonymityExplorer.getAnonymitySetForTxId(txid, levels, windowDays).forEach(i -> row.add(i));
      table.add(row);
    }
    System.out.println(printTable("Anonymity set sizes for txid: " + txid, table) + "\n");

  }
}
