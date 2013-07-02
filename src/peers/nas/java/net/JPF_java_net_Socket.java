package nas.java.net;

import nas.java.net.choice.NasSchedulingChoices;
import nas.java.net.connection.Connections;
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
  
  public void sendConnectionRequest__Ljava_lang_String_2I__V (MJIEnv env, int socketRef, int hostRef, int port) {
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
    } else if (!ti.isFirstStepInsn()){
      String host = env.getStringObject(hostRef);
      if(!hostExists(env, host)) {
        return;
      }

      Connections.Connection conn = connections.getServerConn(port, host);

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
        System.out.println("conn: " + conn);
        int serverRef = conn.getServer();
        System.out.println("env.getElementInfo(serverRef): " + env.getElementInfo(serverRef));
        int tiRef = env.getElementInfo(serverRef).getReferenceField("waitingThread");
        ThreadInfo tiAccept = env.getThreadInfoForObjRef(tiRef);    
        if (tiAccept == null || tiAccept.isTerminated()){
          return;
        }
        
        SystemState ss = env.getSystemState();
        int lockRef = env.getReferenceField( serverRef, "acceptLock");
        ElementInfo lock = env.getModifiableElementInfo(lockRef);

        if (tiAccept.getLockObject() == lock){
          VM vm = VM.getVM();
          env.getModifiableElementInfo(serverRef).setReferenceField("tempSocket",socketRef);
          lock.notifies(ss, ti, false);
          // connection is established with a server, then just set the client info
          connections.setClientInfoFor(conn, socketRef, vm.getApplicationContext(socketRef), host);

          //ChoiceGenerator<?> cg = env.getSchedulerFactory().createUnparkCG(tiAccept);
          ChoiceGenerator<?> cg = NasSchedulingChoices.createConnectCG(ti);
          if (cg != null){
            ss.setNextChoiceGenerator(cg);
            env.repeatInvocation();
          }
          System.out.println("Client> done sending ...");
        }
      }
      // there is a server accept but it is connected to some other client. In this case
      // the client blocks until it gets picked up by server acceptance.
      else {
        System.out.println("Client> A server is there but not waiting - server: " + conn.getServer() + " - client: " + socketRef);
        connections.addNewPendingClientConn(socketRef, port, host);
        int lock = env.getReferenceField( socketRef, "connectLock");
        ElementInfo ei = env.getModifiableElementInfo(lock);

        env.getElementInfo(socketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());
        ei.wait(ti, 0, false);

        assert ti.isWaiting();

        ChoiceGenerator<?> cg = NasSchedulingChoices.createBlockingConnectCG(ti);
        env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking Socket.connect()");
        env.repeatInvocation();
      }
      System.out.println("Client> done sending request!");
      System.out.println("Connections: " + connections.curr);
    }
  }

  @MJI
  public void closeConnection____V (MJIEnv env, int socketRef) {
    int inputStreamRef = env.getElementInfo(socketRef).getReferenceField("input");
    int bufferRef = env.getElementInfo(inputStreamRef).getReferenceField("buffer");
    env.getModifiableElementInfo(bufferRef).setBooleanField("closed", true);
    connections.closeConnections(socketRef);
  }
}
