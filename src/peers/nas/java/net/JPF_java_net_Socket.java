package nas.java.net;

import nas.java.net.choice.NasThreadChoice;
import nas.java.net.choice.Scheduler;
import nas.java.net.connection.ConnectionManager;
import nas.java.net.connection.Connection;
import gov.nasa.jpf.JPFException;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The native peer class for our java.net.Socket
 *
 * @author Nastaran Shafiei
 */
public class JPF_java_net_Socket extends NativePeer {
  private static Map<Integer,Integer> fallbackHashes = new ConcurrentHashMap<>();
  private static int hashCounter = 1;
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
  public void $init____V(MJIEnv env, int socketRef) {
    try {
      // Try to set fields normally
      env.setBooleanField(socketRef, "bound", false);
      env.setBooleanField(socketRef, "connected", false);
      env.setBooleanField(socketRef, "closed", false);
      env.setIntField(socketRef, "timeout", 0);
      synchronized (JPF_java_net_Socket.class) {
        env.setIntField(socketRef, "hash", hashCounter++);
      }

      int lock = env.newObject("java.lang.Object");
      env.setReferenceField(socketRef, "lock", lock);

    }  catch (Exception e) {
      synchronized (JPF_java_net_Socket.class) {
        fallbackHashes.put(socketRef, hashCounter++);
      }
    }
  }



  protected boolean waitForServerAccept(MJIEnv env, int socketRef, int port, String host) {
    // check if such serverSocket exists at all, if so client.connect needs to block
    int existingServer = connections.getServerSocketRef(port, host);
    boolean serverExists = false;

    if(existingServer != MJIEnv.NULL) {
      ElementInfo ei = env.getElementInfo(existingServer);

      if(ei != null && !ei.getBooleanField("closed")) {
       // there exists a sever, so block the client until server accepts
        blockClientConnect(env, socketRef, port, host);
        serverExists = true;
      }
    }

    return serverExists;
  }

  protected boolean handleInjectedExceptionCg(MJIEnv env) {
    ThreadInfo ti = env.getThreadInfo();

    if(Scheduler.failure_injection) {
      ChoiceGenerator<?> cg = env.getChoiceGenerator();

      if(cg!=null && (cg instanceof NasThreadChoice)) {
        NasThreadChoice ncg = (NasThreadChoice)cg;
        if(ncg.isExceptionChoice()) {
          String e = ncg.getExceptionForCurrentChoice();
          ti.createAndThrowException(e, "Injected at Socket.connect()");
          return true;
        }
      }
    }
    return false;
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

      // UPDATE HASH and CONNECTION STATUS when connection is established
      setConnected(env, clientEndSocket, true);
      updateSocketHash(env, clientEndSocket);

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

    connections.addNewPendingClientConn(env, socketRef, port, host);

    int lock = env.getReferenceField( socketRef, "lock");
    ElementInfo ei = env.getModifiableElementInfo(lock);

    env.getElementInfo(socketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());

    ei.wait(ti, 0, false);

    assert ti.isWaiting();

    String[] exceptions = getInjectedExceptions();

    ChoiceGenerator<?> cg = Scheduler.createBlockingConnectCG(ti, exceptions);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking Socket.connect()");
    env.repeatInvocation();
  }

  @MJI
  public void close____V (MJIEnv env, int socketRef) {
    ThreadInfo ti = env.getThreadInfo();

    boolean closed = env.getElementInfo(socketRef).getBooleanField("closed");

    if(ti.isFirstStepInsn()) { // re-execute
      // it shouldn't be closed yet
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

      // unblock blocking-read if there is any
      unblockRead(env, socketRef);

      // set the close status of the socket to true
      setCloseStatus(env, socketRef);

      // closes the socket connection
      connections.closeConnection(socketRef);

      return;
    } else { // first time
      // check if there was any established connection, OW, just return
      Connection conn = connections.getConnection(socketRef);
      if(conn == null) {
        setCloseStatus(env, socketRef);
        return;
      }

      // before closing the socket, creates a choice generator and re-execute
      if(!closed) {
        String[] exceptions = getInjectedExceptions();
        ChoiceGenerator<?> cg = Scheduler.createSocketCloseCG(ti, exceptions);
        env.setMandatoryNextChoiceGenerator(cg, "no CG on Socket.close()");
        env.repeatInvocation();
        return;
      }
    }
  }


  void setCloseStatus(MJIEnv env, int socketRef) {
    env.getModifiableElementInfo(socketRef).setBooleanField("closed", true);

    // UPDATE HASH when socket is closed
    updateSocketHash(env, socketRef);
  }
  /**
   *  for now we check if the connection is "established" and if the other end is "blocked",
   *  if so we just conclude this blocking read
   */
  // TODO: note that this might not be enough when we include blocking write, it has to
  // be extended then
  protected void unblockRead(MJIEnv env, int socketRef) {
    Connection conn = connections.getConnection(socketRef);

    if(conn == null || !conn.isEstablished()) {
      // note that we are looking for blockedRead, therefore the connection has to
      // be established by now or the other end has been terminated.
      return;
    }

    int blockedReader;
    if(conn.isClientEndSocket(socketRef)) {
      blockedReader = conn.getServerEndSocket();
    } else {
      blockedReader = conn.getClientEndSocket();
    }

    // TODO - we don't need this check once we address shutdown semantics
    // check if the reader has been already garbage collected
    if(env.getElementInfo(blockedReader)==null) {
      return;
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

  // Note: Closing a socket doesn't clear its binding state, which means this method
  // will return true for a closed socket (see isClosed()) if it was successfuly bound
  // prior to being closed. true if the socket was successfuly bound to an address.
  @MJI
  public boolean isConnected____Z(MJIEnv env, int socketRef) {
    // Check both the connection manager and socket field
    Connection conn = connections.getConnection(socketRef);
    boolean hasConnection = (conn != null && !conn.isPending());

    // Also check the socket's connected field if available
    try {
      boolean fieldConnected = env.getBooleanField(socketRef, "connected");
      return hasConnection || fieldConnected;
    } catch (Exception e) {
      // Field not available, use connection manager only
      return hasConnection;
    }
  }


  protected String[] getInjectedExceptions() {

    if(Scheduler.failure_injection) {
      String[] exceptions = {Scheduler.IO_EXCEPTION};
      return exceptions;
    }

    return Scheduler.EMPTY;
  }



  @MJI
  public int getInputStream____Ljava_io_InputStream_2(MJIEnv env, int socketRef) {
    try {
      int existingInputRef = env.getReferenceField(socketRef, "input");
      if (existingInputRef != MJIEnv.NULL) {
        return existingInputRef;
      }
    } catch (Exception e) {
      // Input field might not exist
    }

    try {
      int inputStreamRef = env.newObject("java.net.SocketInputStream");
      env.setReferenceField(inputStreamRef, "socket", socketRef);

      try {
        env.setReferenceField(socketRef, "input", inputStreamRef);
      } catch (Exception cacheEx) {
        // Input field might not exist in Socket model
      }

      return inputStreamRef;
    } catch (Exception e) {
      return MJIEnv.NULL;
    }
  }


  @MJI
  public void setSoTimeout__I__V(MJIEnv env, int socketRef, int timeout) {
    env.setIntField(socketRef, "timeout", timeout);
    updateSocketHash(env, socketRef); // Update hash when socket properties change
  }

  @MJI
  /**
   *  this sends connection request without blocking, i.e., if there is not any server
   *  waiting, it throw an IOException
   */

  public void connect__Ljava_lang_String_2I__V (MJIEnv env, int socketRef, int hostRef, int port) {
    ThreadInfo ti = env.getThreadInfo();

    if (ti.isFirstStepInsn()){ // re-executed
      if(handleInjectedExceptionCg(env)) {
        return;
      }

      // notified | timedout | interrupted -> running
      switch (ti.getState()) {
        case NOTIFIED:
        case TIMEDOUT:
        case INTERRUPTED:
          ti.resetLockRef();
          ti.setRunning();
          Connection conn = connections.getConnection(socketRef);
          if(conn != null && conn.isEstablished()) {
            setConnected(env, socketRef, true);
          }
          break;
        default:
          // nothing
      }

      // adding null check before calling isPending()
      Connection conn = connections.getConnection(socketRef);
      if(conn != null && conn.isPending()) {
        boolean closedServer = env.getElementInfo(conn.getServerPassiveSocket()).getBooleanField("closed");
        if(closedServer) {
          connections.terminateConnection(conn);
        } else {
          throw new JPFException("Unidentified connection status in native Socket.connect()");
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

      Connection conn = connections.getPendingServerConn(port, host);

      // there is no pending server accept associated with this address
      if(conn==null) {
        // first check if the client can wait for ServerSocket.accept(), if not throw an exception
        if(!waitForServerAccept(env, socketRef, port, host)) {
          env.throwException("java.io.IOException");
        }
      } else {
        assert(conn.isPending());

        // there is a server accept which is pending (i.e., waiting for a client request).
        // we connect this client to the pending server and unblock the server
        unblockServerAccept(env, socketRef, conn);

        assert(conn.isEstablished());
        setConnected(env, socketRef, true);
      }
    }
  }

  @MJI
  public void $init__Ljava_lang_String_2I__V(MJIEnv env, int socketRef, int hostRef, int port) {
    // Initialize the socket first
    $init____V(env, socketRef);

    // Then connect
    connect__Ljava_lang_String_2I__V(env, socketRef, hostRef, port);
  }


  //  method to update hash when socket state changes
  private enum HashUpdateType {
    STATE_CHANGE,
    DATA_WRITE
  }

  private void updateSocketHash(MJIEnv env, int socketRef) {
    updateSocketHash(env, socketRef, 0, HashUpdateType.STATE_CHANGE);
  }

  private void updateSocketHash(MJIEnv env, int socketRef, int data, HashUpdateType type) {
    try {
      int currentHash = env.getIntField(socketRef, "hash");
      int newHash;

      switch(type) {
        case STATE_CHANGE:
          newHash = currentHash + 1;
          break;
        case DATA_WRITE:
          newHash = currentHash ^ data;
          break;
        default:
          newHash = currentHash;
      }

      env.setIntField(socketRef, "hash", newHash);
    } catch (Exception e) {
      // Use fallback storage
      int currentHash = fallbackHashes.getOrDefault(socketRef, 0);
      int newHash;

      switch(type) {
        case STATE_CHANGE:
          newHash = currentHash + 1;
          break;
        case DATA_WRITE:
          newHash = currentHash ^ data;
          break;
        default:
          newHash = currentHash;
      }

      fallbackHashes.put(socketRef, newHash);
    }
  }

  @MJI
  public int getOutputStream____Ljava_io_OutputStream_2(MJIEnv env, int socketRef) {
    try {
      int existingOutputRef = env.getReferenceField(socketRef, "output");
      if (existingOutputRef != MJIEnv.NULL) {
        return existingOutputRef;
      }
    } catch (Exception e) {
      // No existing output field
    }

    try {
      int outputStreamRef = env.newObject("java.net.SocketOutputStream");

      try {
        env.setReferenceField(outputStreamRef, "socket", socketRef);
        env.setReferenceField(socketRef, "output", outputStreamRef);
        JPF_java_io_OutputStream.registerSocketOutputStream(socketRef, outputStreamRef);
      } catch (Exception e) {
        // Continue if field setting fails
      }

      return outputStreamRef;
    } catch (Exception e1) {
      return MJIEnv.NULL;
    }
  }


  public static void updateSocketHashForDataWrite(MJIEnv env, int socketRef, int data) {
    try {
      // Try direct field access first
      int currentHash = env.getIntField(socketRef, "hash");
      int newHash = currentHash ^ data;
      env.setIntField(socketRef, "hash", newHash);
    } catch (Exception e) {
      // Use fallback storage
      int currentHash = fallbackHashes.getOrDefault(socketRef, 0);
      int newHash = currentHash ^ data;
      fallbackHashes.put(socketRef, newHash);

      //  to sync back to field periodically
      try {
        env.setIntField(socketRef, "hash", newHash);
      } catch (Exception syncEx) {
      }
    }
  }

  //  method to update socket connection status
  private void setConnected(MJIEnv env, int socketRef, boolean connected) {
    try {
      env.setBooleanField(socketRef, "connected", connected);
      updateSocketHash(env, socketRef);
    } catch (Exception e) {
    }
  }

}
