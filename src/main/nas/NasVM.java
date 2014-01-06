package nas;

import nas.java.net.choice.Scheduler;
import nas.java.net.connection.ConnectionManager;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.MultiProcessVM;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.choice.MultiProcessThreadChoice;

public class NasVM extends MultiProcessVM{
  boolean startWithTarget0;
  
  public NasVM (JPF jpf, Config conf) {
    super(jpf, conf);
    
    startWithTarget0 = config.getBoolean("vm.nas.start_with_target0", false);
  }

  @Override
  protected void initSubsystems (Config config) {
    super.initSubsystems(config);
    Scheduler.init(config);
    ConnectionManager.init();
  }
  
  @Override
  protected ChoiceGenerator<?> getInitialCG () {
    if(!startWithTarget0) {
      return super.getInitialCG();
    } else {
      ThreadInfo[] runnables = {getThreadList().getAllMatching(vm.getTimedoutRunnablePredicate())[0]};
      return new MultiProcessThreadChoice("<root>", runnables, true);
    }
  }
}
