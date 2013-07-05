package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import nas.java.net.choice.NasSchedulingChoices;
import gov.nasa.jpf.vm.NativePeer;
import nas.java.net.connection.Connections;
import nas.java.net.connection.Connections.Connection;

public class JPF_java_net_SocketOutputStream extends NativePeer {
  Connections connections = Connections.getConnections();
  
  @MJI
  public void write__I__V (MJIEnv env, int objRef, int value) {
    int clientEnd = JPF_java_net_SocketInputStream.getClientEnd(env, objRef);
    Connection conn = connections.getConnection(clientEnd);
    
    // if it is empty, then there might be a read() waiting for someone to write 
    if(JPF_java_net_SocketInputStream.isBufferEmpty(env, objRef, conn)) {
      unblockRead(env, objRef, conn);
    }
    
    writeByte(env, objRef, value, conn);
  }
  
  @MJI
  public void write___3B__V (MJIEnv env, int objRef, int dataRef) {
    int clientEnd = JPF_java_net_SocketInputStream.getClientEnd(env, objRef);
    Connection conn = connections.getConnection(clientEnd);
    
    // if it is empty, then there might be a read() waiting for someone to write 
    if(JPF_java_net_SocketInputStream.isBufferEmpty(env, objRef, conn)) {
      unblockRead(env, objRef, conn);
    }
    
    writeByteArray(env, objRef, dataRef, conn);
  }
  
  // unblocks a read which was waiting on an empty buffer
  protected void unblockRead(MJIEnv env, int streamRef, Connection conn) {
    ThreadInfo ti = env.getThreadInfo();
    int blockedReader = getOtherEndpoint(env, streamRef, conn);
    
    int tiRef = env.getElementInfo(blockedReader).getReferenceField("waitingThread");
    ThreadInfo tiRead = env.getThreadInfoForObjRef(tiRef);    
    if (tiRead == null || tiRead.isTerminated()){
      return;
    }
    
    SystemState ss = env.getSystemState();
    int lockRef = env.getReferenceField( blockedReader, "lock");
    ElementInfo lock = env.getModifiableElementInfo(lockRef);
    
    if (tiRead.getLockObject() == lock){
      env.getModifiableElementInfo(blockedReader).setReferenceField("waitingThread", MJIEnv.NULL);
      
      lock.notifies(ss, ti, false);
      
      ChoiceGenerator<?> cg = NasSchedulingChoices.createWriteCG(ti);
      if (cg != null){
        ss.setNextChoiceGenerator(cg);
        // env.repeatInvocation(); - no need to re-execute
      }
    }
  }
  
  // writes a single byte value into this buffer
  protected void writeByte(MJIEnv env, int streamRef, int value, Connection conn) {
    if(JPF_java_net_SocketInputStream.isClientAccess(env, streamRef)) {
      conn.clientWrite((byte)value);
      if(conn.isClient2ServerBufferEmpty()) {
        throw new RuntimeException();
      }
    } else {
      conn.serverWrite((byte)value);
      if(conn.isServer2ClientBufferEmpty()) {
        throw new RuntimeException();
      }
    }
  }
  
  // writes an array of byte, represented by dataRef, into this buffer
  protected void writeByteArray(MJIEnv env, int streamRef, int arrValue, Connection conn) {
    byte[] values = env.getByteArrayObject(arrValue);
    
    // TODO: for now we just assume, buffers never go out of space. We need to
    // handle full buffer blocking writes at some point
    for(int i=0; i<values.length; i++) {
      writeByte(env, streamRef, values[i], conn);
    }
  }
  
  protected static int getOtherEndpoint(MJIEnv env, int streamRef, Connection conn) {
    if(JPF_java_net_SocketInputStream.isClientAccess(env, streamRef)) {
      return conn.getServer();
    } else {
      return conn.getClient();
    }
  }
}
