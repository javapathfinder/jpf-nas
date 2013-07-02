package nas.java.net;

import nas.java.net.choice.NasSchedulingChoices;
import nas.java.net.connection.Connections;
import nas.java.net.connection.Connections.Connection;
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

/**
 * The native peer class for our java.net.ServerSocket
 * 
 * @author Nastaran Shafiei
 */
public class JPF_java_net_ServerSocket extends NativePeer {
  Connections connections = Connections.getConnections();

  @MJI
  public void CheckForAddressAlreadyInUse__I__V (MJIEnv env, int serverSocketRef, int port) { 
    boolean inUse = connections.isAddressInUse(getServerHost(env, serverSocketRef), port);
    if(inUse) {
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
    for(int i=0; i<waitingSockets.length; i++) {
      if(waitingSockets[i]==socket) {
        return arrRef;
      }
    }

    int newArrRef = env.newObjectArray("java.net.ServerSocket", waitingSockets.length+1);
    
    for(int i=0; i<waitingSockets.length; i++) {
      env.getModifiableElementInfo(newArrRef).setReferenceElement(i, waitingSockets[i]);
    }
    env.getModifiableElementInfo(newArrRef).setReferenceElement(waitingSockets.length, serverSocketRef);

    return newArrRef;
  }
  
  public String getServerHost(MJIEnv env, int serverSocketRef) {
    VM vm = VM.getVM();
    ApplicationContext appContext = vm.getApplicationContext(serverSocketRef);
    String host = appContext.getHost();
    return host;
  }

  public int getServerPort(MJIEnv env, int serverSocketRef) {
    int implRef = env.getElementInfo(serverSocketRef).getReferenceField("impl");
    int port = env.getElementInfo(implRef).getIntField("localPort");
    return port;
  }
  
  // this is a specialized, native wait that does not require a lock, and that can
  // be turned off by a preceding unpark() call (which is not accumulative)
  // park can be interrupted, but it doesn't throw an InterruptedException, and it doesn't clear the status
  // it can only be called from the current (parking) thread
  @MJI
  public void acceptConnectionRequest____V (MJIEnv env, int serverSocketRef) {
    ThreadInfo ti = env.getThreadInfo();

    if (ti.isFirstStepInsn()){ // re-executed
      // notified | timedout | interrupted -> running
      switch (ti.getState()) {
        case NOTIFIED:
        case TIMEDOUT:
        case INTERRUPTED:
          ti.resetLockRef();
          ti.setRunning();
          break;
        default:
          // nothing
      }
    } else {
      String serverHost = getServerHost(env, serverSocketRef); 
      int port = getServerPort(env, serverSocketRef);
      Connection conn = connections.getClientConn(port, serverHost);
      
      // In this case, there a client that is waiting to connect to this server,
      // then let's establish the connection and unblock the waiting client thread
      if(conn!=null && conn.isPending()) {
        System.out.println("Server> connecting to the pending client: " + conn.getClient());
        int clientRef = conn.getClient();
        
        int tiRef = env.getElementInfo(clientRef).getReferenceField("waitingThread");
        ThreadInfo tiConnect = env.getThreadInfoForObjRef(tiRef);    
        if (tiConnect == null || tiConnect.isTerminated()){
          return;
        }
        
        SystemState ss = env.getSystemState();
        int lockRef = env.getReferenceField( clientRef, "connectLock");
        ElementInfo lock = env.getModifiableElementInfo(lockRef);

        if (tiConnect.getLockObject() == lock){
          VM vm = VM.getVM();
          env.getModifiableElementInfo(serverSocketRef).setReferenceField("tempSocket",clientRef);
          lock.notifies(ss, ti, false);
          // connection is established with a client, then just set the server info
          //conn.setServerInfo(serverSocketRef, connections.getApplicationContext(serverSocketRef));
          connections.setServerInfoFor(conn, serverSocketRef, vm.getApplicationContext(serverSocketRef));

          ChoiceGenerator<?> cg = NasSchedulingChoices.createAcceptCG(ti);
          if (cg != null){
            ss.setNextChoiceGenerator(cg);
            env.repeatInvocation();
          }
          System.out.println("Server> done accepting and unblocking the client ...");
        }
      } 
      // there is no client waiting to connect to this server, therefore let's create
      // a new server and blocks it until it receives a connection request from a client
      else {
        System.out.println("Server> no client is waiting - creating new connection");
        connections = Connections.getConnections();
        connections.addNewPendingServerConn(serverSocketRef, port, serverHost);
        
        int lock = env.getReferenceField( serverSocketRef, "acceptLock");
        ElementInfo ei = env.getModifiableElementInfo(lock);
        env.getElementInfo(serverSocketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());
        
        ei.wait(ti, 0, false);

        assert ti.isWaiting();

        // note we pass in the timeout value, since this might determine the type of CG that is created
        ChoiceGenerator<?> cg = NasSchedulingChoices.createBlockingAcceptCG(ti);
        env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking ServerSocket.accept()");
        env.repeatInvocation();
      }
    }
  }

  @MJI
  public int getConnectedClientSocket____Ljava_net_Socket_2 (MJIEnv env, int objRef) {
    int rSocket = MJIEnv.NULL;
    return rSocket;
  }

  @MJI
  public void closeConnections____V (MJIEnv env, int serverSocketRef) {
    connections.closeConnections(serverSocketRef);
  }
}
