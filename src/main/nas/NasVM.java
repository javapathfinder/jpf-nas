package nas;

import nas.java.net.choice.Scheduler;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.vm.MultiProcessVM;

public class NasVM extends MultiProcessVM{

  public NasVM (JPF jpf, Config conf) {
    super(jpf, conf);
  }

  @Override
  protected void initSubsystems (Config config) {
    super.initSubsystems(config);
    Scheduler.init(config);
  }
}
