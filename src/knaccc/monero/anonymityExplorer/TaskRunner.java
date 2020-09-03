package knaccc.monero.anonymityExplorer;

import java.io.File;
import java.util.Date;

public class TaskRunner {

  public void run() {
    try {

      String daemonEndpoint = "http://127.0.0.1:18081";
      File storageDir = new File(System.getProperty("user.home") + "/.anonymityExplorer");
      AnonymityExplorer anonymityExplorer = new AnonymityExplorer(storageDir, daemonEndpoint);
      anonymityExplorer.readLatestBlocks();

      long startMs = new Date().getTime();
      task.run(anonymityExplorer);
      System.out.println("Duration: " + (new Date().getTime() - startMs) / 1000 + "s");
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private final Task task;
  public TaskRunner(Task task) {
    this.task = task;
  }

  // if called from a Task, this will instantiate the Task and run it
  public static void runMe() throws Exception {
    var callingClassName = Thread.currentThread().getStackTrace()[2].getClassName();
    Task task = (Task) Class.forName(callingClassName).getConstructor().newInstance();
    new TaskRunner(task).run();
  }

}
