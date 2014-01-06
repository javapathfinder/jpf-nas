package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.ThreadInfo.State;
import nas.java.net.choice.NasThreadChoice;
import nas.java.net.choice.Scheduler;
import gov.nasa.jpf.vm.NativePeer;
import nas.java.net.connection.ConnectionManager;
import nas.java.net.connection.Connection;

public class JPF_java_net_SocketInputStream extends NativePeer {
  static int EOF = -1;
  
  ConnectionManager connections = ConnectionManager.getConnections();
  
  @MJI
  public int read____I (MJIEnv env, int objRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    // Note that we can only retrieve the connection using the client end cause
    // serverSocket can be connected to multiple clients at the time
    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    Connection conn = connections.getConnection(socketRef);
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now attempt to read
      
      if(ti.getState() == State.TIMEDOUT) { // handle timedout read
        this.handleTimedoutRead(env, ti, socketRef);
        assert ti.isRunnable();
        return EOF;
      } else if(isInjectedFailureChoice(env)) {
        return EOF;
      } else if(conn.isClosed()) { // this blocking read got unblocked upon closing socket
        return EOF;
      } else {
        return readByte(conn, socketRef);
      }
    } else {
      if(isConnBroken(env, objRef, conn)) {
        return EOF;
      }
      
      if(isReadBufferEmpty(conn, socketRef)) {
        blockRead(env, objRef, conn, socketRef);
        env.repeatInvocation(); // re-execute needed once server gets interrupted
        return -1;
      } else {
        return readByte(conn, socketRef);
      }
    }
  }
  
  @MJI
  public int read___3BII__I (MJIEnv env, int objRef, int bufferRef, int off, int len) {
    ThreadInfo ti = env.getThreadInfo();
    
    // if len is zero, then no bytes are read and 0 is returned
    if(len == 0) {
      return 0;
    }
    
    // Note that we can only retrieve the connection using the client end cause
    // serverSocket can be connected to multiple clients at the time
    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    Connection conn = connections.getConnection(socketRef);
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now attempt to read
      
      if(ti.getState() == State.TIMEDOUT) { // handle timedout read
        this.handleTimedoutRead(env, ti, socketRef);
        assert ti.isRunnable();
        return EOF;
      } else if(isInjectedFailureChoice(env)) {
        return EOF;
      } else if(conn.isClosed()) { // this blocking read got unblocked upon closing socket
        return EOF;
      } else {      
        return readByteArray(env, bufferRef, conn, socketRef, off, len);
      }
    } else {
      if(isConnBroken(env, objRef, conn)) {
        return EOF;
      }
      
      if(isReadBufferEmpty(conn, socketRef)) {
        blockRead(env, objRef, conn, socketRef);
        env.repeatInvocation(); // re-execute is needed once server gets interrupted
        return -1;
      } else {
        return readByteArray(env, bufferRef, conn, socketRef, off, len);
      }
    }
  }
  
  protected void handleTimedoutRead(MJIEnv env, ThreadInfo ti, int socketRef) {
    assert ti.getState() == State.TIMEDOUT;
    
    // release the monitor & reset the thread state to running
    env.getModifiableElementInfo(socketRef).setReferenceField("waitingThread", MJIEnv.NULL);
    ti.resetLockRef();
    ti.setRunning();
      
    // make JPF to throw SocketTimeoutException, that indicates required time has elapsed
    env.throwException("java.net.SocketTimeoutException", "Read timed out");
    
    return;
  }
  
  protected boolean isInjectedFailureChoice(MJIEnv env) {
    boolean isInjectedFailureChoice = false;
    ThreadInfo ti = env.getThreadInfo();
    
    if(Scheduler.injectFailures()) {
      ChoiceGenerator<?> cg = env.getChoiceGenerator(); 
      
      if(cg!=null && (cg instanceof NasThreadChoice)) {
        NasThreadChoice ncg = (NasThreadChoice)cg;
        if(ncg.isExceptionChoice()) {
          String e = ncg.getExceptionForCurrentChoice();
          ti.createAndThrowException(e, "Injected at SocketInputStream.read()");
          isInjectedFailureChoice = true;
        }
      }
    }
    
    return isInjectedFailureChoice;
  }
  
  /**
   * Reading on a closed or terminated connection requires throwing an exception
   * 
   * @return true if there is no exception, OW false
   */
  protected boolean isConnBroken(MJIEnv env, int objRef, Connection conn) {
    boolean isConnBroken = false;
    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    
    // if this end is closed, an exception should be thrown. If the socket at the 
    // other end is closed just return EOF
    if(conn.isClosed()) {
      if(isThisEndClosed(env, objRef)) {
        env.throwException("java.net.SocketException", "Socket closed");
      } else if(isReadBufferEmpty(conn, socketRef)) {
        env.throwException("java.net.SocketException", "Connection reset");
      }
      isConnBroken = true;
    }
    
    return isConnBroken;
  }
  
  public static boolean isThisEndClosed(MJIEnv env, int streamRef) {
    int socket = env.getElementInfo(streamRef).getReferenceField("socket");
    boolean closed =env.getElementInfo(socket).getBooleanField("closed");
    return closed;
  }
  
  @MJI
  public int available____I (MJIEnv env, int streamRef) {
    int socketRef = env.getElementInfo(streamRef).getReferenceField("socket");
    Connection conn = connections.getConnection(socketRef);

    if(conn.isClientEndSocket(socketRef)) {
      return conn.server2ClientBufferSize();
    } else {
      return conn.client2ServerBufferSize();
    }
  }
  
  protected int getTimeout(MJIEnv env, int socketRef) {
    return env.getElementInfo(socketRef).getIntField("timeout");
  }
  
  // makes the current thread get block on an empty buffer
  protected void blockRead(MJIEnv env, int streamRef, Connection conn, int endpoint) {
    ThreadInfo ti = env.getThreadInfo();
    int timeout = getTimeout(env, endpoint);
    
    int reader = getAccessor(conn, endpoint);
    int lock = env.getElementInfo(reader).getReferenceField("lock");
    
    ElementInfo ei = env.getModifiableElementInfo(lock);
    env.getModifiableElementInfo(reader).setReferenceField("waitingThread", ti.getThreadObjectRef());
    
    ei.wait(ti, timeout, false);
    
    assert ti.isWaiting();
    
    String[] exceptions = getInjectedExceptions(env, streamRef);
    
    ChoiceGenerator<?> cg = Scheduler.createBlockingReadCG(ti, exceptions);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking InputStream.read()");
  }
  
  // reads a single byte and returns its value
  protected byte readByte(Connection conn, int endpoint) {
    if(conn.isClientEndSocket(endpoint)) {
      return conn.clientRead();
    } else {
      return conn.serverRead();
    }
  }
  
  // reads "some" bytes into a given array, represented by desArrRef and returns the
  // number of byte which are read
  protected int readByteArray(MJIEnv env, int desArrRef, Connection conn, int endpoint, int off, int len) {
    int n=0;
    int i = off;
    
    while(!isReadBufferEmpty(conn, endpoint) && n<len) {
      byte b = readByte(conn, endpoint);
      env.getModifiableElementInfo(desArrRef).setByteElement(i++, b);
      n++;
    }
    
    return n;
  }
  
  protected boolean isEndOfStream(byte b) {
    char c = (char)b;
    return ((c == '\n') || (c == '\r'));
  }
  
  protected static boolean isReadBufferEmpty(Connection conn, int endpoint) {
    if(conn.isClientEndSocket(endpoint)) {
      return conn.isServer2ClientBufferEmpty();
    } else {
      return conn.isClient2ServerBufferEmpty();
    }
  }
  
  protected static int getAccessor(Connection conn, int endpoint) {
    if(conn.isClientEndSocket(endpoint)) {
      return conn.getClientEndSocket();
    } else {
      return conn.getServerEndSocket();
    }
  }
  
  protected String[] getInjectedExceptions(MJIEnv env, int objRef) {
    if(Scheduler.failure_injection) {
      int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
      int timeout = env.getElementInfo(socketRef).getIntField("timeout");
      
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
  
  protected static void printReader(Connection conn, int endpoint) {
    String result;
    if(conn.isClientEndSocket(endpoint)) {
      result = "Client Reading";
    } else {
      result = "Server Reading";
    }
    System.out.println(result);
  }
}
