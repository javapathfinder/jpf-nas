package nas.java.net;

import java.util.List;

import gov.nasa.jpf.JPFException;
import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NativePeer;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import gov.nasa.jpf.vm.ThreadInfo.State;
import nas.java.net.choice.NasThreadChoice;
import nas.java.net.choice.Scheduler;
import nas.java.net.connection.ConnectionManager;
import nas.java.net.connection.Connection;

/**
 * The native peer class for our java.net.ServerSocket
 * 
 * @author Nastaran Shafiei
 */
public class JPF_java_net_ServerSocket extends NativePeer {
  ConnectionManager connections = ConnectionManager.getConnections();


  public String getServerHost (MJIEnv env, int serverSocketRef) {
    VM vm = VM.getVM();
    ApplicationContext appContext = vm.getApplicationContext(serverSocketRef);
    String host = appContext.getHost();
    return host;
  }

  public int getServerPort (MJIEnv env, int serverSocketRef) {
    int implRef = env.getElementInfo(serverSocketRef).getReferenceField("impl");
    int port = env.getElementInfo(implRef).getIntField("localPort");
    return port;
  }

  @MJI
  public int accept0____Ljava_net_Socket_2(MJIEnv env, int serverSocketRef) {
    ThreadInfo ti = env.getThreadInfo();

    if (ti.isFirstStepInsn()) { // re-executed

      if(handleInjectedExceptionCg(env)) {
        return MJIEnv.NULL;
      }

      int acceptedSocket = resetAndGetAcceptedSocket(env, serverSocketRef);

      // If no cached socket, create one
      if (acceptedSocket == MJIEnv.NULL) {
        acceptedSocket = env.newObject("java.net.Socket");
        int impl = env.newObject("java.net.PlainSocketImpl");
        env.setReferenceField(acceptedSocket, "impl", impl);

        // Initialize socket fields for Java 11 compatibility
        env.setBooleanField(acceptedSocket, "created", true);
        env.setBooleanField(acceptedSocket, "bound", true);
        env.setBooleanField(acceptedSocket, "connected", true);
        env.setBooleanField(acceptedSocket, "closed", false);

        env.getModifiableElementInfo(serverSocketRef).setReferenceField("acceptedSocket", acceptedSocket);
      }

      switch (ti.getState()) {
        case TIMEDOUT:
          handleTimedoutAccept(env, ti, serverSocketRef);
          assert ti.isRunnable();
          return MJIEnv.NULL;

        case RUNNING:
        case UNBLOCKED:
          return acceptedSocket;

        default:
          throw new JPFException("The state of the thread cannot be recognized in the re-execute of ServerSocket.accept()");
      }

    } else { // First execution

      // Check if socket is closed
      if(isClosed(env, serverSocketRef)) {
        env.throwException("java.net.SocketException", "Socket is closed");
        resetAndGetAcceptedSocket(env, serverSocketRef);
        return MJIEnv.NULL;
      }

      // Try to connect to any pending client first
      if(connectToPendingClient(env, serverSocketRef)) {
        env.repeatInvocation();
        return MJIEnv.NULL;
      }

      // No pending clients - check if there are any OTHER processes that might connect
      // If this is a single-process test with a timeout, we should timeout immediately
      // because no client will ever connect
      int timeout = getTimeout(env, serverSocketRef);
      if (timeout > 0 && !hasOtherRunnableProcesses(env)) {
        // Single process with timeout and no pending clients = immediate timeout
        env.throwException("java.net.SocketTimeoutException", "Accept timed out");
        return MJIEnv.NULL;
      }

      // Block and wait for a client connection
      blockServerAccept(env, serverSocketRef);
      return MJIEnv.NULL;
    }
  }

  /**
   * Check if there are other runnable processes that might potentially connect
   */
  private boolean hasOtherRunnableProcesses(MJIEnv env) {
    VM vm = VM.getVM();
    ThreadInfo currentTi = env.getThreadInfo();
    ThreadInfo[] runnables = vm.getThreadList().getAllMatching(vm.getTimedoutRunnablePredicate());

    // Check if there are threads from other application contexts
    ApplicationContext currentCtx = vm.getApplicationContext(currentTi.getThreadObjectRef());
    for (ThreadInfo ti : runnables) {
      if (ti != currentTi && !ti.isTerminated()) {
        ApplicationContext tiCtx = vm.getApplicationContext(ti.getThreadObjectRef());
        if (tiCtx != currentCtx) {
          return true;
        }
      }
    }
    return false;
  }


  protected boolean handleInjectedExceptionCg(MJIEnv env) {
    ThreadInfo ti = env.getThreadInfo();
    
    if(Scheduler.failure_injection) {
      ChoiceGenerator<?> cg = env.getChoiceGenerator(); 
      
      if(cg!=null && (cg instanceof NasThreadChoice)) {
        NasThreadChoice ncg = (NasThreadChoice)cg;
        if(ncg.isExceptionChoice()) {
          String e = ncg.getExceptionForCurrentChoice();
          ti.createAndThrowException(e, "Injected at ServerSocket.accept()");
          return true;
        }
      }
    }
    return false;
  }
  
  public boolean connectToPendingClient(MJIEnv env, int serverSocketRef) {
    String serverHost = getServerHost(env, serverSocketRef);
    int port = getServerPort(env, serverSocketRef);
    
    List<Connection> conns = connections.getAllPendingClientConn(port, serverHost);
    
    for(Connection conn: conns) {
      unblockClientConnect(env, serverSocketRef, conn);
      if(conn.isEstablished()) {
        return true;
      } else {
        connections.terminateConnection(conn);
      }
    }
    
    return false;
  }
  
  
  protected int resetAndGetAcceptedSocket(MJIEnv env, int serverSocketRef) {
    int acceptedSocketCache = env.getElementInfo(serverSocketRef).getReferenceField("acceptedSocket");
    env.getModifiableElementInfo(serverSocketRef).setReferenceField("acceptedSocket", MJIEnv.NULL);
    return acceptedSocketCache;
  }
  
  protected int getTimeout(MJIEnv env, int serverSocketRef) {
    return env.getElementInfo(serverSocketRef).getIntField("timeout");
  }
  
  protected boolean isClosed(MJIEnv env, int serverSocketRef) {
    return env.getElementInfo(serverSocketRef).getBooleanField("closed");
  }

  protected void handleTimedoutAccept(MJIEnv env, ThreadInfo ti, int serverSocketRef) {
    assert ti.getState() == State.TIMEDOUT;
    
    // release the monitor & reset the thread state to running
    env.getModifiableElementInfo(serverSocketRef).setReferenceField("waitingThread", MJIEnv.NULL);
    ti.resetLockRef();
    ti.setRunning();
    
    // make JPF to throw SocketTimeoutException, that indicates required time has elapsed
    env.throwException("java.net.SocketTimeoutException", "Accept timed out");
    
    String serverHost = getServerHost(env, serverSocketRef);
    int port = getServerPort(env, serverSocketRef);
    Connection conn =  connections.getPendingServerConn(port, serverHost);
    
    // we need to terminate the connection to avoid sockets from connecting to this server
    if (conn != null) {
      connections.terminateConnection(conn);
    }
    
    return;
  }

  protected void blockServerAccept (MJIEnv env, int serverSocketRef) {
    ThreadInfo ti = env.getThreadInfo();
    int timeout = getTimeout(env, serverSocketRef);

    String serverHost = getServerHost(env, serverSocketRef);
    int port = getServerPort(env, serverSocketRef);

    connections = ConnectionManager.getConnections();
    connections.addNewPendingServerConn(env, serverSocketRef, port, serverHost);

    int lock = env.getReferenceField(serverSocketRef, "lock");
    ElementInfo ei = env.getModifiableElementInfo(lock);
    env.getModifiableElementInfo(serverSocketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());

    ei.wait(ti, timeout, false);

    assert ti.isWaiting();

    String[] exceptions = getInjectedExceptions(env, serverSocketRef);
    
    ChoiceGenerator<?> cg = Scheduler.createBlockingAcceptCG(ti, exceptions);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking ServerSocket.accept()");
    env.repeatInvocation(); // re-execute needed in case blocking server some
                            // how get interrupted
  }

  protected void unblockClientConnect (MJIEnv env, int serverSocketRef, Connection conn) {
    ThreadInfo ti = env.getThreadInfo();

    int clientRef = conn.getClientEndSocket();

    int tiRef = env.getElementInfo(clientRef).getReferenceField("waitingThread");
    ThreadInfo tiConnect = env.getThreadInfoForObjRef(tiRef);
    
    // TODO - handle this case in accept0()
    if (tiConnect == null || tiConnect.isTerminated()) {
      return; 
    }

    SystemState ss = env.getSystemState();
    int lockRef = env.getReferenceField(clientRef, "lock");
    ElementInfo lock = env.getModifiableElementInfo(lockRef);

    if (tiConnect.getLockObject() == lock) {
      VM vm = VM.getVM();

      int acceptedSocket = env.getElementInfo(serverSocketRef).getReferenceField("acceptedSocket");

      env.getModifiableElementInfo(clientRef).setReferenceField("waitingThread", MJIEnv.NULL);

      lock.notifies(ss, ti, false);

      // connection is established with a client, then just set the server info
      conn.establishedConnWithServer(serverSocketRef, acceptedSocket, vm.getApplicationContext(serverSocketRef));

      String[] exceptions = getInjectedExceptions(env, serverSocketRef); 
      
      ChoiceGenerator<?> cg = Scheduler.createAcceptCG(ti, exceptions);
      if (cg != null) {
        ss.setNextChoiceGenerator(cg);
        env.repeatInvocation();
      }
    }
  }
  
  @MJI
  public void close____V (MJIEnv env, int serverSocketRef) {
    env.getModifiableElementInfo(serverSocketRef).setBooleanField("closed", true);
    
    String serverHost = getServerHost(env, serverSocketRef);
    int port = getServerPort(env, serverSocketRef);
    List<Connection> conns = connections.getAllPendingClientConn(port, serverHost);
    
    for(Connection conn: conns) {
      unblockClientConnect(env, serverSocketRef, conn);
    }
  }
  
  protected String[] getInjectedExceptions(MJIEnv env, int serverSocketRef) {
    
    if(Scheduler.failure_injection) {
      int timeout = env.getElementInfo(serverSocketRef).getIntField("timeout");
      if(timeout==0) {
        String[] exceptions = {Scheduler.IO_EXCEPTION};
        return exceptions;
      } else {
        String[] exceptions = {Scheduler.IO_EXCEPTION, Scheduler.TIMEOUT_EXCEPTION};
        return exceptions;
      }
    }
    
    return Scheduler.EMPTY;
  }

  @MJI
  public void CheckForAddressAlreadyInUse__I__V(MJIEnv env, int serverSocketRef, int port) {
    String host = getServerHost(env, serverSocketRef);
    int existingServer = connections.getServerSocketRef(port, host);

    if (existingServer != MJIEnv.NULL && existingServer != serverSocketRef) {
      ElementInfo ei = env.getElementInfo(existingServer);
      if (ei != null && !ei.getBooleanField("closed")) {
        env.throwException("java.net.BindException", "Address already in use: " + port);
      }
    }
  }
}
