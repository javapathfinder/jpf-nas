package nas.java.net.choice;

import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.ThreadList;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.choice.ThreadChoiceFromSet;

/**
 * Methods of this class creates choices communication points
 * 
 * @author Nastaran Shafiei
 */
public class NasSchedulingChoices {

  public static final String ACCEPT = "ACCEPT";
  public static final String BLOCKING_ACCEPT = "BLOCKING_ACCEPT";
  public static final String CONNECT = "CONNECT";
  public static final String BLOCKING_CONNECT = "BLOCKING_CONNECT";
  public static final String WRITE = "WRITE";
  public static final String BLOCKING_READ = "BLOCKING_READ";

  protected static ThreadInfo[] getRunnables(ThreadInfo ti) {
    ThreadList tl = VM.getVM().getThreadList();
    return tl.getRunnableThreads();
  }

  /**
   * Creates a choice generator upon SocketServer.accept() which makes the server wait
   * for a connection request
   */
  public static ChoiceGenerator<ThreadInfo> createBlockingAcceptCG (ThreadInfo tiAccept){
    SystemState ss = VM.getVM().getSystemState();

    if (ss.isAtomic()) {
      ss.setBlockedInAtomicSection();
    }

    return new ThreadChoiceFromSet( BLOCKING_ACCEPT, getRunnables(tiAccept), true);
  }

  /**
   * Creates a choice generator upon Socket.connect() which makes the client to sent
   * a connection request to the server
   */
  public static ChoiceGenerator<ThreadInfo> createConnectCG (ThreadInfo tiConnect){
    return new ThreadChoiceFromSet( CONNECT, getRunnables(tiConnect), true);
  }

  public static ChoiceGenerator<ThreadInfo> createBlockingConnectCG (ThreadInfo tiConnect){
    SystemState ss = VM.getVM().getSystemState();

    if (ss.isAtomic()) {
      ss.setBlockedInAtomicSection();
    }

    return new ThreadChoiceFromSet( BLOCKING_CONNECT, getRunnables(tiConnect), true);
  }

  public static ChoiceGenerator<ThreadInfo> createAcceptCG (ThreadInfo tiConnect){
    return new ThreadChoiceFromSet( ACCEPT, getRunnables(tiConnect), true);
  }

  /**
   * Creates a choice generator upon Socket.read() which makes the client/server wait
   * on an empty buffer, until the other end-point writes something
   */
  public static ChoiceGenerator<ThreadInfo> createBlockingReadCG (ThreadInfo tiRead){
    SystemState ss = VM.getVM().getSystemState();

    if (ss.isAtomic()) {
      ss.setBlockedInAtomicSection();
    }

    return new ThreadChoiceFromSet( BLOCKING_READ, getRunnables(tiRead), true);
  }

  public static ChoiceGenerator<ThreadInfo> createWriteCG (ThreadInfo tiConnect){
    return new ThreadChoiceFromSet( WRITE, getRunnables(tiConnect), true);
  }
}
