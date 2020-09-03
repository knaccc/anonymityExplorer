package knaccc.monero.anonymityExplorer.tasks;

import knaccc.monero.anonymityExplorer.AnonymityExplorer;
import knaccc.monero.anonymityExplorer.Task;
import knaccc.monero.anonymityExplorer.TaskRunner;

import java.util.Set;
import java.util.stream.Collectors;

public class MergeDetect implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    int maxChurnBlockDistance = 30*24*30;

    System.out.println("churn window (hours): " + (maxChurnBlockDistance / 30));
    
    var incomingOutputIdsForBotfinex = Set.of(19575002L, 19589339L, 19568553L, 19589038L, 19588189L, 19588717L);

    System.out.println(anonymityExplorer.getTxIdsWhereOutputSetsSpentTogether(
      incomingOutputIdsForBotfinex.stream().map(id -> anonymityExplorer.getForwardOutputIdRefs(id, 2, maxChurnBlockDistance)).collect(Collectors.toSet())));


  }

}
