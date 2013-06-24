package gov.nasa.jpf.vm;

import gov.nasa.jpf.vm.choice.ThreadChoiceFromSet;

/**
 * Methods of this class creates choices communication points
 * 
 * @author Nastaran Shafiei
 */
public class NasSchedulingChoices {

  public static final String ACCEPT = "ACCEPT";
  public static final String CONNECT = "CONNECT";

  protected static ThreadInfo[] getRunnables(ThreadInfo ti) {
    ThreadList tl = VM.getVM().getThreadList();
    return tl.getRunnableThreads();
  }

  /**
   * Creates a choice generator upon SocketServer.accept() which makes the server waits
   * for a connection request
   */
  public static ChoiceGenerator<ThreadInfo> createAcceptCG (ThreadInfo tiAccept){
    SystemState ss = VM.getVM().getSystemState();

    if (ss.isAtomic()) {
      ss.setBlockedInAtomicSection();
    }

    return new ThreadChoiceFromSet( ACCEPT, getRunnables(tiAccept), true);
  }

  /**
   * Creates a choice generator upon Socket.connect() which makes the client to sent
   * a connection request to the server
   */
  public static ChoiceGenerator<ThreadInfo> createConnectCG (ThreadInfo tiConnect){
    return new ThreadChoiceFromSet( CONNECT, getRunnables(tiConnect), true);
  }
}
