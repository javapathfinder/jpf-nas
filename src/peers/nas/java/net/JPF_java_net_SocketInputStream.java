package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.ThreadInfo;
import nas.java.net.choice.NasThreadChoice;
import nas.java.net.choice.Scheduler;
import gov.nasa.jpf.vm.NativePeer;
import nas.java.net.connection.ConnectionManager;
import nas.java.net.connection.ConnectionManager.Connection;

public class JPF_java_net_SocketInputStream extends NativePeer {
  static int EOF = -1;
  
  ConnectionManager connections = ConnectionManager.getConnections();
  
  @MJI
  public int read____I (MJIEnv env, int objRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    // Note that we can only retrieve the connection using the client end cause
    // serverSocket can be connected to multiple clients at the time
    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    int clientEnd = getClientEnd(env, socketRef);
    Connection conn = connections.getConnection(clientEnd);
    
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now do the read()
      if(isInjectedFailureChoice(env)) {
        return EOF;
      } else {
        return readByte(env, objRef, conn);
      }
    } else {
      if(isConnBroken(env, objRef, conn)) {
        return EOF;
      }
      
      if(isBufferEmpty(env, objRef, conn)) {
        blockRead(env, objRef, conn);
        env.repeatInvocation(); // re-execute needed once server gets interrupted
        return -1;
      } else {
        return readByte(env, objRef, conn);
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
    int clientEnd = getClientEnd(env, socketRef);
    Connection conn = connections.getConnection(clientEnd);
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now do the read()      
      if(isInjectedFailureChoice(env)) {
        return EOF;
      } else {      
        return readByteArray(env, objRef, bufferRef, conn, off, len);
      }
    } else {
      if(isConnBroken(env, objRef, conn)) {
        return EOF;
      }
      
      if(isBufferEmpty(env, objRef, conn)) {
        blockRead(env, objRef, conn);
        env.repeatInvocation(); // re-execute is needed once server gets interrupted
        return -1;
      } else {
        return readByteArray(env, objRef, bufferRef, conn, off, len);
      }
    }
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
    
    // if this end is closed, an exception should be thrown. If the socket at the 
    // other end is closed just return EOF
    if(conn.isClosed()) {
      if(isThisEndClosed(env, objRef)) {
        env.throwException("java.net.SocketException", "Socket closed");
      }
      isConnBroken = true;
    } else if(conn.isTerminated()) {
      env.throwException("java.net.SocketException", "connection is terminated");
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
    int clientEnd = getClientEnd(env, socketRef);
    Connection conn = connections.getConnection(clientEnd);
    
    if(isClientAccess(env, streamRef)) {
      return conn.server2ClientBufferSize();
    } else {
      return conn.client2ServerBufferSize();
    }
  }
  
  // makes the current thread get block on an empty buffer
  protected void blockRead(MJIEnv env, int streamRef, Connection conn) {
    ThreadInfo ti = env.getThreadInfo();
    
    int reader = getAccessor(env, streamRef, conn);
    int lock = env.getElementInfo(reader).getReferenceField("lock");
    
    ElementInfo ei = env.getModifiableElementInfo(lock);
    env.getModifiableElementInfo(reader).setReferenceField("waitingThread", ti.getThreadObjectRef());
    
    ei.wait(ti, 0, false);
    
    assert ti.isWaiting();
    
    String[] exceptions = getInjectedExceptions(env, streamRef);
    
    ChoiceGenerator<?> cg = Scheduler.createBlockingReadCG(ti, exceptions);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking InputStream.read()");
  }
  
  // reads a single byte and returns its value
  protected int readByte(MJIEnv env, int streamRef, Connection conn) {
    if(isClientAccess(env, streamRef)) {
      return conn.clientRead();
    } else {
      return conn.serverRead();
    }
  }
  
  // reads "some" bytes into a given array, represented by desArrRef and returns the
  // number of byte which are read
  protected int readByteArray(MJIEnv env, int streamRef, int desArrRef, Connection conn, int off, int len) {
    int n=0;
    int i = off;
    
    while(!isBufferEmpty(env, streamRef, conn) && n<len) {
      int value = readByte(env, streamRef, conn);
      env.getModifiableElementInfo(desArrRef).setByteElement(i++, (byte)value);
      n++;
    }
    
    return n;
  }
  
  protected static boolean isBufferEmpty(MJIEnv env, int streamRef, Connection conn) {  
    if(isClientAccess(env, streamRef)) {
      return conn.isServer2ClientBufferEmpty();
    } else {
      return conn.isClient2ServerBufferEmpty();
    }
  }
  
  protected static int getClientEnd(MJIEnv env, int socketRef) {
    // first check if this inputStream is for a client or a server
    int clientEndRef = env.getElementInfo(socketRef).getReferenceField("clientEnd");
    int clientRef;
    
    if(clientEndRef==MJIEnv.NULL) {
      clientRef = socketRef;
    } else {
      clientRef = clientEndRef;
    }
    
    return clientRef;
  }
  
  protected static boolean isClientAccess(MJIEnv env, int streamRef) {
    int socketRef = env.getElementInfo(streamRef).getReferenceField("socket");
    int clientEndRef = env.getElementInfo(socketRef).getReferenceField("clientEnd");
    return (clientEndRef==MJIEnv.NULL);
  }
  
  protected static int getAccessor(MJIEnv env, int streamRef, Connection conn) {
    if(isClientAccess(env, streamRef)) {
      return conn.getClient();
    } else {
      return conn.getServer();
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
  
  protected static void printReader(MJIEnv env, int streamRef) {
    String result;
    if(JPF_java_net_SocketInputStream.isClientAccess(env, streamRef)) {
      result = "Client Reading";
    } else {
      result = "Server Reading";
    }
    System.out.println(result);
  }
}
