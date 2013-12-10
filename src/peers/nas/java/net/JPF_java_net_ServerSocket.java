package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NativePeer;
import gov.nasa.jpf.vm.SystemClassLoaderInfo;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
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

  @MJI
  public void CheckForAddressAlreadyInUse__I__V (MJIEnv env, int serverSocketRef, int port) {
    boolean inUse = connections.isAddressInUse(getServerHost(env, serverSocketRef), port);
    if (inUse) {
      env.throwException("java.net.BindException", "Address already in use");
    }
  }

  @MJI
  public int addToWaitingSockets__Ljava_net_ServerSocket_2___3Ljava_net_ServerSocket_2 (MJIEnv env, int serverSocketRef, int socket) {
    ClassInfo ci = SystemClassLoaderInfo.getSystemResolvedClassInfo("java.net.ServerSocket");
    ElementInfo ei = ci.getStaticElementInfo();

    int arrRef = ei.getReferenceField("waitingSockets");

    int[] waitingSockets = env.getReferenceArrayObject(arrRef);

    // check if it is already among the waiting sockets
    for (int i = 0; i < waitingSockets.length; i++) {
      if (waitingSockets[i] == socket) { return arrRef; }
    }

    int newArrRef = env.newObjectArray("java.net.ServerSocket", waitingSockets.length + 1);

    for (int i = 0; i < waitingSockets.length; i++) {
      env.getModifiableElementInfo(newArrRef).setReferenceElement(i, waitingSockets[i]);
    }
    env.getModifiableElementInfo(newArrRef).setReferenceElement(waitingSockets.length, serverSocketRef);

    return newArrRef;
  }

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
  public void accept0____V (MJIEnv env, int serverSocketRef) {
    ThreadInfo ti = env.getThreadInfo();

    if (ti.isFirstStepInsn()) { // re-executed
      // notified | timedout | interrupted -> running
      switch (ti.getState()) {
      // Note - excluded the case for the NOTIFIED state, why?
      case TIMEDOUT: // TODO - how to deal with timeout? Look into
                     // "object.wait()"
      case INTERRUPTED:
        ti.resetLockRef();
        ti.setRunning();
        // is that right?!
        env.getModifiableElementInfo(serverSocketRef).setReferenceField("acceptedSocket", MJIEnv.NULL);
        break;
      default:
        // nothing
      }
      
      if(Scheduler.failure_injection) {
        ChoiceGenerator<?> cg = env.getChoiceGenerator(); 
        
        if(cg!=null && (cg instanceof NasThreadChoice)) {
          NasThreadChoice ncg = (NasThreadChoice)cg;
          if(ncg.isExceptionChoice()) {
            String e = ncg.getExceptionForCurrentChoice();
            ti.createAndThrowException(e, "Injected at ServerSocket.accept()");
          }
        }
      }
    } else {
      String serverHost = getServerHost(env, serverSocketRef);
      int port = getServerPort(env, serverSocketRef);
      Connection conn = connections.getPendingClientConn(port, serverHost);
      
      if(isClosed(env, serverSocketRef)) {
        env.throwException("java.net.SocketException", "Socket is closed");
        return;
      }
      
      // In this case, there a client that is waiting to connect to this server,
      // then let's establish the connection and unblock the waiting client
      // thread
      if (conn != null && conn.isPending()) {
        unblockClientConnect(env, serverSocketRef, conn);
      }
      // there is no client waiting to connect to this server, therefore let's
      // create a new server connection and blocks it until it receives a connection
      // request from a client
      else {
        blockServerAccept(env, serverSocketRef);
      }
    }
  }

  protected boolean isClosed(MJIEnv env, int serverSocketRef) {
    return env.getElementInfo(serverSocketRef).getBooleanField("closed");
  }
  
  protected void unblockClientConnect (MJIEnv env, int serverSocketRef, Connection conn) {
    ThreadInfo ti = env.getThreadInfo();

    int clientRef = conn.getClientEndSocket();

    int tiRef = env.getElementInfo(clientRef).getReferenceField("waitingThread");
    ThreadInfo tiConnect = env.getThreadInfoForObjRef(tiRef);
    if (tiConnect == null || tiConnect.isTerminated()) { return; }

    SystemState ss = env.getSystemState();
    int lockRef = env.getReferenceField(clientRef, "lock");
    ElementInfo lock = env.getModifiableElementInfo(lockRef);

    if (tiConnect.getLockObject() == lock) {
      VM vm = VM.getVM();

      int acceptedSocket = env.getElementInfo(serverSocketRef).getReferenceField("acceptedSocket");

      env.getModifiableElementInfo(serverSocketRef).setReferenceField("waitingThread", MJIEnv.NULL);

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

  protected void blockServerAccept (MJIEnv env, int serverSocketRef) {
    ThreadInfo ti = env.getThreadInfo();

    String serverHost = getServerHost(env, serverSocketRef);
    int port = getServerPort(env, serverSocketRef);

    connections = ConnectionManager.getConnections();
    connections.addNewPendingServerConn(serverSocketRef, port, serverHost);

    int lock = env.getReferenceField(serverSocketRef, "lock");
    ElementInfo ei = env.getModifiableElementInfo(lock);
    env.getElementInfo(serverSocketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());

    ei.wait(ti, 0, false);

    assert ti.isWaiting();

    String[] exceptions = getInjectedExceptions(env, serverSocketRef);
    
    ChoiceGenerator<?> cg = Scheduler.createBlockingAcceptCG(ti, exceptions);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking ServerSocket.accept()");
    env.repeatInvocation(); // re-execute needed in case blocking server some
                            // how get interrupted
  }

  @MJI
  public void close____V (MJIEnv env, int serverSocketRef) {
    env.getModifiableElementInfo(serverSocketRef).setBooleanField("closed", true);
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
}
