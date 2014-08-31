package nas;

import nas.java.net.choice.Scheduler;
import nas.java.net.connection.ConnectionManager;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.MultiProcessVM;
import gov.nasa.jpf.vm.SyncPolicy;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.choice.ThreadChoiceFromSet;

public class NasVM extends MultiProcessVM{
  int initiatingTarget;
  
  public NasVM (JPF jpf, Config conf) {
    super(jpf, conf);
    
    initiatingTarget = config.getInt("vm.nas.initiating_target", -1);
  }

  @Override
  protected void initSubsystems (Config config) {
    super.initSubsystems(config);
    Scheduler.init(config);
    ConnectionManager.init();
  }
  
  @Override
  public void setRootCG (){
    if(initiatingTarget >= 0) {
      ThreadInfo[] runnables =  getThreadList().getAllMatching(vm.getTimedoutRunnablePredicate());
      ChoiceGenerator<ThreadInfo> cg = new ThreadChoiceFromSet(SyncPolicy.ROOT, new ThreadInfo[]{runnables[initiatingTarget]}, true);
      getSystemState().setMandatoryNextChoiceGenerator( cg, "no ROOT choice generator");
    } else {
      super.setRootCG();
    }
  }
}
