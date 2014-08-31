package nas.java.net.choice;

import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.choice.ExceptionThreadChoiceFromSet;

public class NasThreadChoice extends ExceptionThreadChoiceFromSet {

  public NasThreadChoice (String id, ThreadInfo[] runnables, ThreadInfo exceptionThread, String[] exceptionClsNames) {
    super(id, runnables, exceptionThread, (exceptionClsNames==null?Scheduler.EMPTY:exceptionClsNames));
  }
  
  public boolean isExceptionChoice() {
    String e = getExceptionForCurrentChoice();
    return (e!=null && e.length()>0);
  }
}
