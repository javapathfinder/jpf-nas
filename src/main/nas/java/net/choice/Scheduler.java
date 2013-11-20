package nas.java.net.choice;

import gov.nasa.jpf.Config;
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
public class Scheduler {

  public static final String ACCEPT = "ACCEPT";
  public static final String BLOCKING_ACCEPT = "BLOCKING_ACCEPT";
  public static final String CONNECT = "CONNECT";
  public static final String BLOCKING_CONNECT = "BLOCKING_CONNECT";
  public static final String WRITE = "WRITE";
  public static final String BLOCKING_READ = "BLOCKING_READ";
  public static final String SOCKET_CLOSE = "SOCKET_CLOSE";
  public static final String EXCEPTIONS_INJECT = "EXCEPTIONS_INJECT";

  public static final String IO_EXCEPTION = "java.io.IOException";
  public static final String TIMEOUT_EXCEPTION = "java.net.SocketTimeoutException";
  public static final String[] EMPTY = new String[0];
  
  public static boolean failure_injection;
  
  /**
   * This is invoked by VM.initSubsystems()
   */
  public static void init (Config config) {
    failure_injection = config.getBoolean("scheduler.failure_injection", false);
  }
  
  protected static ThreadInfo[] getRunnables(ThreadInfo ti) {
    ThreadList tl = VM.getVM().getThreadList();
    return tl.getRunnableThreads();
  }

  /**
   * Creates a choice generator upon SocketServer.accept() which makes the server wait
   * for a connection request
   */
  public static ChoiceGenerator<ThreadInfo> createBlockingAcceptCG (ThreadInfo tiAccept, String[] failures){
    SystemState ss = VM.getVM().getSystemState();

    if (ss.isAtomic()) {
      ss.setBlockedInAtomicSection();
    }
    
    return new NasThreadChoice( BLOCKING_ACCEPT, getRunnables(tiAccept), tiAccept, failures);
  }

  public static ChoiceGenerator<ThreadInfo> createAcceptCG (ThreadInfo tiConnect, String[] failures){
    return new NasThreadChoice( ACCEPT, getRunnables(tiConnect), tiConnect, failures);
  }
  
  /**
   * Creates a choice generator upon Socket.connect() which makes the client to sent
   * a connection request to the server
   */
  public static ChoiceGenerator<ThreadInfo> createConnectCG (ThreadInfo tiConnect, String[] failures){
    return new NasThreadChoice( CONNECT, getRunnables(tiConnect), tiConnect, failures);
  }

  public static ChoiceGenerator<ThreadInfo> createBlockingConnectCG (ThreadInfo tiConnect, String[] failures){
    SystemState ss = VM.getVM().getSystemState();

    if (ss.isAtomic()) {
      ss.setBlockedInAtomicSection();
    }

    return new NasThreadChoice( BLOCKING_CONNECT, getRunnables(tiConnect), tiConnect, failures);
  }

  /**
   * Creates a choice generator upon Socket.read() which makes the client/server wait
   * on an empty buffer, until the other end-point writes something
   */
  public static ChoiceGenerator<ThreadInfo> createBlockingReadCG (ThreadInfo tiRead, String[] failures){
    return new NasThreadChoice( BLOCKING_READ, getRunnables(tiRead), tiRead, failures);
  }

  public static ChoiceGenerator<ThreadInfo> createWriteCG (ThreadInfo tiWrite, String[] failures){
    return new NasThreadChoice( WRITE, getRunnables(tiWrite), tiWrite, failures);
  }
  
  /**
   * Creates a choice generator right before closing a socket
   */
  public static ChoiceGenerator<ThreadInfo> createSocketCloseCG (ThreadInfo tiClose, String[] failures){
    return new NasThreadChoice( SOCKET_CLOSE, getRunnables(tiClose), tiClose, failures);
  }
  
  public static ChoiceGenerator<ThreadInfo> injectExceptions (ThreadInfo ti, String[] exceptions){
    ThreadInfo[] runnable = {ti};
    return new NasThreadChoice( EXCEPTIONS_INJECT, runnable, ti, exceptions);
  }
}
