package knaccc.monero.util;

import java.util.Date;

public class Timer {

  String desc;
  long startMs;
  long lastPrintTimestampMs = -1;
  int lastMinsPrinted = -1;
  public Timer(String desc) {
    this.desc = desc+" ";
    startMs = new Date().getTime();
  }
  public Timer() {
    this.desc = "";
    startMs = new Date().getTime();
  }
  public void printProgress(int currentIterationBeginning, int totalIterations) {
    if(currentIterationBeginning==0) return;
    long nowMs = new Date().getTime();
    long timeElapsedMs = nowMs - startMs;
    long msPerIteration = (long) (((double)timeElapsedMs) / ((double)currentIterationBeginning));
    long totalTimeEstimatedMs = totalIterations*msPerIteration;
    long estimatedRimeRemainingMs = totalTimeEstimatedMs - timeElapsedMs;

    int minsRemaining = (int) Math.ceil(((double) estimatedRimeRemainingMs)/60000d);
    if((lastMinsPrinted!=minsRemaining) || (nowMs - lastPrintTimestampMs)>60*1000) {
      System.out.println(desc + "progress: " + currentIterationBeginning + "/" + totalIterations + ", est. time remaining (mins): " + minsRemaining);
      lastPrintTimestampMs = nowMs;
      lastMinsPrinted = minsRemaining;
    }
  }

}
