package knaccc.monero.anonymitySet.tasks;

import knaccc.monero.anonymitySet.AnonymityExplorer;
import knaccc.monero.anonymitySet.Task;
import knaccc.monero.anonymitySet.TaskRunner;

import java.util.*;

public class PathTrace implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }


  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    // Tracking Trinance -> Botfinex

    var possibleOutgoingOutputIdsForTrinance = Set.of(19571003L, 19572539L, 19573608L, 19571534L, 19565055L, 19570252L, 19568546L, 19573030L, 19569557L);
    System.out.println("possibleOutgoingOutputIdsForTrinance: " + possibleOutgoingOutputIdsForTrinance);

    var incomingOutputIdsForBotfinex = Set.of(19575002L, 19589339L, 19568553L, 19589038L, 19588189L, 19588717L);
    System.out.println("incomingOutputIdsForBotfinex: " + incomingOutputIdsForBotfinex);

    anonymityExplorer.printPaths(possibleOutgoingOutputIdsForTrinance, incomingOutputIdsForBotfinex, 4);

  }


}
