package nas.java.net;

import nas.java.net.choice.NasSchedulingChoices;
import nas.java.net.connection.Connections;
import nas.java.net.connection.Connections.Connection;
import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.MultiProcessVM;
import gov.nasa.jpf.vm.NativePeer;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

/**
 * The native peer class for our java.net.Socket
 * 
 * @author Nastaran Shafiei
 */
public class JPF_java_net_Socket extends NativePeer {
  Connections connections = Connections.getConnections();

  protected boolean hostExists(MJIEnv env, String host) {
    ApplicationContext[] appContext = MultiProcessVM.getVM().getApplicationContexts();
    for(int i=0; i<appContext.length; i++) {
      if(appContext[i].getHost().equals(host)) {
        return true;
      }
    }
    // TODO - should throw exception! which exception?
    env.throwException("java.net.UnknownHostException");
    return false;
  }
  
  @MJI
  /**
   *  this sends connection request without blocking, i.e., if there is not any server 
   *  waiting, it throw an IOException
   */
  
  public void connect__Ljava_lang_String_2I__V (MJIEnv env, int socketRef, int hostRef, int port) {
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
    } else { // first time
      String host = env.getStringObject(hostRef);
      if(!hostExists(env, host)) {
        return;
      }

      Connection conn = connections.getServerPendingConn(port, host);

      // there is no server accept associated with this address
      if(conn==null && !connections.hasServerConn(port, host)) {
        env.throwException("java.io.IOException");
        return;
      }       
      // there is a server accept which is pending (i.e., waiting for the client request).
      // In this case, we connect this client to the pending server and unblock the server
      else if(conn!=null && conn.isPending()){
        unblockServerAccept(env, socketRef, conn);
      }
      // there is a server accept but it is connected to some other client. In this case
      // the client blocks until it gets picked up by another server accept.
      else {
        blockClientConnect(env, socketRef, port, host);
      }
    }
  }
  
  protected void unblockServerAccept(MJIEnv env, int socketRef, Connection conn) {
    ThreadInfo ti = env.getThreadInfo();
    
    int serverRef = conn.getServer();
    
    int tiRef = env.getElementInfo(serverRef).getReferenceField("waitingThread");
    ThreadInfo tiAccept = env.getThreadInfoForObjRef(tiRef);    
    if (tiAccept == null || tiAccept.isTerminated()){
      return;
    }
    
    SystemState ss = env.getSystemState();
    int lockRef = env.getReferenceField( serverRef, "lock");
    ElementInfo lock = env.getModifiableElementInfo(lockRef);

    if (tiAccept.getLockObject() == lock){
      VM vm = VM.getVM();
      // acceptedSocket is a private field in ServerSocket which is also the return value of 
      // ServerSocket.accept()
      int acceptedSocket = env.getElementInfo(serverRef).getReferenceField("acceptedSocket");
      //setSharedBuffers(env, socketRef, acceptedSocket, serverRef);
      
      env.getModifiableElementInfo(acceptedSocket).setReferenceField("clientEnd", socketRef);
      
      env.getModifiableElementInfo(serverRef).setReferenceField("waitingThread", MJIEnv.NULL);
      
      lock.notifies(ss, ti, false);
      
      // connection is established with a server, then just set the client info
      conn.establishedConnWithClient(socketRef, vm.getApplicationContext(socketRef), conn.getServerHost());

      ChoiceGenerator<?> cg = NasSchedulingChoices.createConnectCG(ti);
      if (cg != null){
        ss.setNextChoiceGenerator(cg);
        // env.repeatInvocation(); 
      }
    }
  }
  
  protected void blockClientConnect(MJIEnv env, int socketRef, int port, String host) {
    ThreadInfo ti = env.getThreadInfo();
    
    connections.addNewPendingClientConn(socketRef, port, host);
    
    int lock = env.getReferenceField( socketRef, "lock");
    ElementInfo ei = env.getModifiableElementInfo(lock);
    
    env.getElementInfo(socketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());
    
    ei.wait(ti, 0, false);

    assert ti.isWaiting();

    ChoiceGenerator<?> cg = NasSchedulingChoices.createBlockingConnectCG(ti);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking Socket.connect()");
    env.repeatInvocation();
  }
  
  @MJI
  public void close____V (MJIEnv env, int socketRef) {
    connections.closeConnections(socketRef);
  }
}
