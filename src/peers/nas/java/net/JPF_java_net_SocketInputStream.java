package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.ThreadInfo;
import nas.java.net.choice.NasSchedulingChoices;
import gov.nasa.jpf.vm.NativePeer;
import nas.java.net.connection.Connections;
import nas.java.net.connection.Connections.Connection;

public class JPF_java_net_SocketInputStream extends NativePeer {
  Connections connections = Connections.getConnections();
  
  @MJI
  public int read____I (MJIEnv env, int objRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    // Note that we can only retrieve the connection using the client end cause
    // serverSocket can be connected to multiple clients at the time
    int clientEnd = getClientEnd(env, objRef);
    Connection conn = connections.getConnection(clientEnd);
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now do the read()
      return readByte(env, objRef, conn);
    } else {
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
  public int read___3B__I (MJIEnv env, int objRef, int desArrRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    // Note that we can only retrieve the connection using the client end cause
    // serverSocket can be connected to multiple clients at the time
    int clientEnd = getClientEnd(env, objRef);
    Connection conn = connections.getConnection(clientEnd);
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now do the read()
      // TODO - explore other cases! maybe it has got interrupted
      return readByteArray(env, objRef, desArrRef, conn);
    } else {
      if(isBufferEmpty(env, objRef, conn)) {
        blockRead(env, objRef, conn);
        env.repeatInvocation(); // re-execute needed once server gets interrupted
        return -1;
      } else {
        return readByteArray(env, objRef, desArrRef, conn);
      }
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
    
    ChoiceGenerator<?> cg = NasSchedulingChoices.createBlockingReadCG(ti);
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
  protected int readByteArray(MJIEnv env, int streamRef, int desArrRef, Connection conn) {
    int len = env.getByteArrayObject(desArrRef).length;

    int n=0;
    while(isBufferEmpty(env, streamRef, conn) && n<len) {
      int value = readByte(env, streamRef, conn);
      env.getModifiableElementInfo(desArrRef).setByteElement(n, (byte)value);
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
  
  protected static int getClientEnd(MJIEnv env, int streamRef) {
    // first check if this inputStream is for a client or a server
    int socketRef = env.getElementInfo(streamRef).getReferenceField("socket");
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
}
