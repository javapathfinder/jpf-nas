package nas.java.net;

import nas.java.net.choice.NasThreadChoice;
import nas.java.net.choice.Scheduler;
import nas.java.net.connection.ConnectionManager;
import nas.java.net.connection.Connection;
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
  ConnectionManager connections = ConnectionManager.getConnections();

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
      
      if(Scheduler.failure_injection) {
        ChoiceGenerator<?> cg = env.getChoiceGenerator(); 
        
        if(cg!=null && (cg instanceof NasThreadChoice)) {
          NasThreadChoice ncg = (NasThreadChoice)cg;
          if(ncg.isExceptionChoice()) {
            String e = ncg.getExceptionForCurrentChoice();
            ti.createAndThrowException(e, "Injected at Socket.connect()");
          }
        }
      }
    } else { // first time
      boolean closed = env.getElementInfo(socketRef).getBooleanField("closed");
      if (closed) {
        env.throwException("java.net.SocketException", "Socket is closed");
        return;
      }
      
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
  
  protected void unblockServerAccept(MJIEnv env, int clientEndSocket, Connection conn) {
    ThreadInfo ti = env.getThreadInfo();
    
    int serverRef = conn.getServerPassiveSocket();
    
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
      
      env.getModifiableElementInfo(serverRef).setReferenceField("waitingThread", MJIEnv.NULL);
      
      lock.notifies(ss, ti, false);
      
      // connection is established with a server, then just set the client info
      conn.establishedConnWithClient(clientEndSocket, vm.getApplicationContext(clientEndSocket), 
                                     conn.getServerHost(), acceptedSocket);

      String[] exceptions = getInjectedExceptions();
      
      ChoiceGenerator<?> cg = Scheduler.createConnectCG(ti, exceptions);
      if (cg != null){
        ss.setNextChoiceGenerator(cg);
        env.repeatInvocation(); 
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

    ChoiceGenerator<?> cg = Scheduler.createBlockingConnectCG(ti, null);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking Socket.connect()");
    env.repeatInvocation();
  }
  
  @MJI
  public void close____V (MJIEnv env, int socketRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    boolean closed = env.getElementInfo(socketRef).getBooleanField("closed");
    
    if(ti.isFirstStepInsn()) { // re-execute
      assert !closed;
      
      if(Scheduler.failure_injection) {
        ChoiceGenerator<?> cg = env.getChoiceGenerator(); 
        
        if(cg!=null && (cg instanceof NasThreadChoice)) {
          NasThreadChoice ncg = (NasThreadChoice)cg;
          if(ncg.isExceptionChoice()) {
            String e = ncg.getExceptionForCurrentChoice();
            ti.createAndThrowException(e, "Injected at Socket.close()");
            return;
          }
        }
      }
      
      // changes the close status of the socket
      env.getModifiableElementInfo(socketRef).setBooleanField("closed", true);
      
      // unblock blocking-read if there is any
      unblockRead(env, socketRef);
      
      // closes the socket connection - Note: closing this socket will also 
      // close its InputStream and OutputStream
      // TODO: check if we need to close IOStream along with socket
      connections.closeConnection(socketRef);
      
      return;
    } else { // first time
      // before closing the socket, creates a choice generator and re-execute the
      // invocation of close()
      if(!closed) {
        String[] exceptions = getInjectedExceptions();
        ChoiceGenerator<?> cg = Scheduler.createSocketCloseCG(ti, exceptions);
        env.setMandatoryNextChoiceGenerator(cg, "no CG on Socket.close()");
        env.repeatInvocation();
        return;
      }
    }
  }
  
  /**
   *  for now we look if the connection is "established" and if the other end is "blocked",
   *  if so we just conclude this blocking read
   */
  // TODO: that this might not be enough when we include blocking write, it has to
  // be extended then
  protected void unblockRead(MJIEnv env, int socketRef) {
    Connection conn = connections.getConnection(socketRef);
    
    if(!conn.isEstablished()) {
      // note that we are looking for blockedRead, therefore the connection has to
      // be established by now or the other end has been terminated.
      return;
    }
    
    int blockedReader;
    if(conn.isClientEndSocket(socketRef)) {
      blockedReader = conn.getServerPassiveSocket();
    } else {
      blockedReader = conn.getClientEndSocket();
    }
    
    int tiRef = env.getElementInfo(blockedReader).getReferenceField("waitingThread");
    ThreadInfo tiRead = env.getThreadInfoForObjRef(tiRef);
    // is the socket thread blocked?
    if (tiRead == null || tiRead.isTerminated()){
      return;
    }
    
    SystemState ss = env.getSystemState();
    int lockRef = env.getReferenceField( blockedReader, "lock");
    ElementInfo lock = env.getModifiableElementInfo(lockRef);
    
    if (tiRead.getLockObject() == lock){
      ThreadInfo ti = env.getThreadInfo();
      env.getModifiableElementInfo(blockedReader).setReferenceField("waitingThread", MJIEnv.NULL);
      
      lock.notifies(ss, ti, false);
    }
  }
  
  protected String[] getInjectedExceptions() {
    
    if(Scheduler.failure_injection) {
      String[] exceptions = {Scheduler.IO_EXCEPTION};
      return exceptions;
    }
    
    return Scheduler.EMPTY;
  }
}
