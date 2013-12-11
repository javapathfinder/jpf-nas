package nas.util.test;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.util.test.TestMultiProcessJPF;

public class TestNasJPF extends TestMultiProcessJPF {
  protected void setTestTargetKeys(Config conf, StackTraceElement testMethod) {
    super.setTestTargetKeys(conf, testMethod);
    conf.put("vm.class", "nas.NasVM");
  }
}
