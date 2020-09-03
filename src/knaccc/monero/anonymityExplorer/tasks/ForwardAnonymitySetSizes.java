package knaccc.monero.anonymityExplorer.tasks;

import knaccc.monero.anonymityExplorer.AnonymityExplorer;
import knaccc.monero.anonymityExplorer.Task;
import knaccc.monero.anonymityExplorer.TaskRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static knaccc.monero.util.TextTableUtil.leftPad;

public class ForwardAnonymitySetSizes implements Task {

  public static void main(String[] args) throws Exception {
    TaskRunner.runMe();
  }

  @Override
  public void run(AnonymityExplorer anonymityExplorer) throws Exception {

    long startOutputId = 19570000L;
    long outputId24HrsLater = anonymityExplorer.getMinOutputIdForBlockId(anonymityExplorer.getTxRecord(startOutputId).block + 30*24);
    var outputIds = new ArrayList<Long>();
    for(int i=0; i<10; i++) outputIds.add((long) ((Math.random()*(outputId24HrsLater-startOutputId))+startOutputId));
    System.out.println("outputIds: " + outputIds);
    List.of(1,2,3,4,5,6,12,24,48,96).forEach(i->{
      String s = outputIds.stream().map(id->leftPad(""+anonymityExplorer.getForwardTxRecordRefs(id, i*30).size(), 6)).collect(Collectors.joining(","));
      System.out.println("observ. period (hours): " + i + ", fwd tx anon set: " + s);
    });

  }
}
