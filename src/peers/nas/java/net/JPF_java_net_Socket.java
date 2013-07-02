package nas.java.net;

import nas.java.net.choice.NasSchedulingChoices;
import nas.java.net.connection.Connections;
import nas.java.net.connection.Connections.Connection;
import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ClassLoaderInfo;
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
    } else if (!ti.isFirstStepInsn()){ // first time
      
      String host = env.getStringObject(hostRef);
      if(!hostExists(env, host)) {
        return;
      }

      Connection conn = connections.getServerConn(port, host);

      System.out.println("Client> sending request ...");
      // there was no server accept associated with this address
      if(conn==null) {
        System.out.println("Client> no server is found - throw IOException - clinet: " + socketRef);
        env.throwException("java.io.IOException");
        return;
      }       
      // there is a server accept which is pending (i.e., waiting for the client request).
      // In this case, we connect this client to the pending server and unblock the server
      else if(conn.isPending()){
        System.out.println("Client> A server is waiting - server: " + conn.getServer() + " - client: " + socketRef);
        unblockServerAccept(env, socketRef, conn);
      }
      // there is a server accept but it is connected to some other client. In this case
      // the client blocks until it gets picked up by another server accept.
      else {
        System.out.println("Client> A server is there but not waiting - server: " + conn.getServer() + " - client: " + socketRef);
        blockClientConnect(env, socketRef, port, host);
      }
      System.out.println("Client> done sending request!");
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
    int lockRef = env.getReferenceField( serverRef, "serverLock");
    ElementInfo lock = env.getModifiableElementInfo(lockRef);

    if (tiAccept.getLockObject() == lock){
      VM vm = VM.getVM();
      // acceptedSocket is a private field in ServerSocket which is also the return value of 
      // ServerSocket.accept()
      int acceptedSocket = env.getElementInfo(serverRef).getReferenceField("acceptedSocket");
      shareIOStreams(env, socketRef, acceptedSocket);
      
      lock.notifies(ss, ti, false);
      
      // connection is established with a server, then just set the client info
      connections.setClientInfoFor(conn, socketRef, vm.getApplicationContext(socketRef), conn.getServerHost());

      ChoiceGenerator<?> cg = NasSchedulingChoices.createConnectCG(ti);
      if (cg != null){
        ss.setNextChoiceGenerator(cg);
        // env.repeatInvocation(); 
      }
      System.out.println("Client> done sending ...");
    }
  }
  
  protected void blockClientConnect(MJIEnv env, int socketRef, int port, String host) {
    ThreadInfo ti = env.getThreadInfo();
    
    connections.addNewPendingClientConn(socketRef, port, host);
    
    int lock = env.getReferenceField( socketRef, "clientLock");
    ElementInfo ei = env.getModifiableElementInfo(lock);
    
    env.getElementInfo(socketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());
    
    ei.wait(ti, 0, false);

    assert ti.isWaiting();

    ChoiceGenerator<?> cg = NasSchedulingChoices.createBlockingConnectCG(ti);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking Socket.connect()");
    env.repeatInvocation();
  }

  /**
   * makes the socket1 to share its buffer with socket2
   *    socket2.input.buffer.data <= socket1.output.buffer.data
   *    socket2.output.buffer.data <= socket1.input.buffer.data
   */
  protected static void shareIOStreams(MJIEnv env, int socket1, int socket2) {
    int inRef1 = env.getElementInfo(socket1).getReferenceField("input");
    int inBufferRef1 = env.getElementInfo(inRef1).getReferenceField("buffer");
    int inDataRef1 = env.getElementInfo(inBufferRef1).getReferenceField("data");
    
    int outRef1 = env.getElementInfo(socket1).getReferenceField("output");
    int outBufferRef1 = env.getElementInfo(outRef1).getReferenceField("buffer");
    int outDataRef1 = env.getElementInfo(outBufferRef1).getReferenceField("data");
    
    int inRef2 = env.getElementInfo(socket2).getReferenceField("input");
    int inBufferRef2 = env.getElementInfo(inRef2).getReferenceField("buffer");
    env.getModifiableElementInfo(inBufferRef2).setReferenceField("data", outDataRef1);
    
    int outRef2 = env.getElementInfo(socket2).getReferenceField("output");
    int outBufferRef2 = env.getElementInfo(outRef2).getReferenceField("buffer");
    env.getModifiableElementInfo(outBufferRef2).setReferenceField("data", inDataRef1);
  }
  
  @MJI
  public void closeConnection____V (MJIEnv env, int socketRef) {
    int inputStreamRef = env.getElementInfo(socketRef).getReferenceField("input");
    int bufferRef = env.getElementInfo(inputStreamRef).getReferenceField("buffer");
    env.getModifiableElementInfo(bufferRef).setBooleanField("closed", true);
    connections.closeConnections(socketRef);
  }
}
