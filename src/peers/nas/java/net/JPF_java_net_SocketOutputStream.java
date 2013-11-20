package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import nas.java.net.choice.NasThreadChoice;
import nas.java.net.choice.Scheduler;
import gov.nasa.jpf.vm.NativePeer;
import nas.java.net.connection.ConnectionManager;
import nas.java.net.connection.ConnectionManager.Connection;

public class JPF_java_net_SocketOutputStream extends NativePeer {
  ConnectionManager connections = ConnectionManager.getConnections();
  
  @MJI
  public void write__I__V (MJIEnv env, int objRef, int value) {
    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    int clientEnd = JPF_java_net_SocketInputStream.getClientEnd(env, socketRef);
    Connection conn = connections.getConnection(clientEnd);
    
    if(conn.isClosed()) {
      String msg;
      if(JPF_java_net_SocketInputStream.isThisEndClosed(env, objRef)) {
        msg = "Socket closed";
      } else {
        msg = "Broken pipe";
      }
      env.throwException("java.net.SocketException", msg);
      return;
    } else if(conn.isTerminated()) {
      env.throwException("java.net.SocketException", "connection is terminated");
      return;
    }
    
    ThreadInfo ti = env.getThreadInfo();
    if(ti.isFirstStepInsn()) { // re-execution
      if(Scheduler.failure_injection) {
        ChoiceGenerator<?> cg = env.getChoiceGenerator(); 
        
        if(cg!=null && (cg instanceof NasThreadChoice)) {
          NasThreadChoice ncg = (NasThreadChoice)cg;
          if(ncg.isExceptionChoice()) {
            String e = ncg.getExceptionForCurrentChoice();
            ti.createAndThrowException(e, "Injected at SocketOutputStream.write()");
          }
        }
      }
    } else {
      // if it is empty, then there might be a read() waiting for someone to write 
      if(JPF_java_net_SocketInputStream.isBufferEmpty(env, objRef, conn)) {
        unblockRead(env, objRef, conn);
      }
      
      writeByte(env, objRef, value, conn);
    }
  }
  
  @MJI
  public void write___3BII__V (MJIEnv env, int objRef, int bufferRef, int off, int len) {
    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    int clientEnd = JPF_java_net_SocketInputStream.getClientEnd(env, socketRef);
    Connection conn = connections.getConnection(clientEnd);
    
    if(conn.isClosed()) {
      String msg;
      if(JPF_java_net_SocketInputStream.isThisEndClosed(env, objRef)) {
        msg = "Socket closed";
      } else {
        msg = "Broken pipe";
      }
      env.throwException("java.net.SocketException", msg);
      return;
    } else if(conn.isTerminated()) {
      env.throwException("java.net.SocketException", "connection is terminated");
      return;
    }
    
    ThreadInfo ti = env.getThreadInfo();
    if(ti.isFirstStepInsn()) { // re-execution
      if(Scheduler.failure_injection) {
        ChoiceGenerator<?> cg = env.getChoiceGenerator(); 
        
        if(cg!=null && (cg instanceof NasThreadChoice)) {
          NasThreadChoice ncg = (NasThreadChoice)cg;
          if(ncg.isExceptionChoice()) {
            String e = ncg.getExceptionForCurrentChoice();
            ti.createAndThrowException(e, "Injected at SocketOutputStream.write()");
          }
        }
      }
    } else {
      // if it is empty, then there might be a read() waiting for someone to write 
      if(JPF_java_net_SocketInputStream.isBufferEmpty(env, objRef, conn)) {
        unblockRead(env, objRef, conn);
      }
      
      writeByteArray(env, objRef, bufferRef, conn, off, len);
    }
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
      
      String[] exceptions = getInjectedExceptions();
      
      ChoiceGenerator<?> cg = Scheduler.createWriteCG(ti, exceptions);
      if (cg != null){
        ss.setNextChoiceGenerator(cg);
        env.repeatInvocation();
      }
    } else if(Scheduler.failure_injection) {
      String[] exceptions = getInjectedExceptions(); 
      
      ChoiceGenerator<?> cg = Scheduler.injectExceptions(ti, exceptions);
      if (cg != null) {
        ss.setNextChoiceGenerator(cg);
        env.repeatInvocation();
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
  protected void writeByteArray(MJIEnv env, int streamRef, int arrValue, Connection conn, int off, int len) {
    byte[] values = env.getByteArrayObject(arrValue);
    
    int i = off;
    
    // TODO: for now we just assume, buffers never go out of space. We need to
    // handle full buffer blocking writes at some point
    for(i=0; i<len; i++) {
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
  
  protected static void printWriter(MJIEnv env, int streamRef) {
    String result;
    if(JPF_java_net_SocketInputStream.isClientAccess(env, streamRef)) {
      result = "Client Writing";
    } else {
      result = "Server Writing";
    }
    System.out.println(result);
  }
  
  protected String[] getInjectedExceptions() {
    
    if(Scheduler.failure_injection) {
      String[] exceptions = {Scheduler.IO_EXCEPTION};
      return exceptions;
    }
    
    return Scheduler.EMPTY;
  }
}
