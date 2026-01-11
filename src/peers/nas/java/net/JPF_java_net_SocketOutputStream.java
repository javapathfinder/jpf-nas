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
import nas.java.net.connection.Connection;

public class JPF_java_net_SocketOutputStream extends NativePeer {
  ConnectionManager connections = ConnectionManager.getConnections();

  @MJI
  public void write__I__V (MJIEnv env, int objRef, int value) {

    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    Connection conn = connections.getConnection(socketRef);


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
      // **KEY CHANGE: ALWAYS update hash first, regardless of connection status**
      updateSocketHash(env, socketRef, value);

      // Only proceed with actual I/O operations if connection exists
      if(conn == null) {
        return; // Hash updated successfully, but no network I/O
      }

      if(isConnBroken(env, objRef, conn)) {
        return;
      }

      // Proceed with actual network I/O operations
      int reader = getOtherEnd(conn, socketRef);

      if(JPF_java_net_SocketInputStream.isReadBufferEmpty(conn, reader)) {
        unblockRead(env, conn, socketRef);
      }

      writeByte(value, conn, socketRef);
    }
  }

  @MJI
  public static void init____V(MJIEnv env, int clsObjRef) {
    // Static initialization method
  }

  private void updateSocketHash(MJIEnv env, int socketRef, int data) {
    // Delegate to Socket peer's coordinated static method
    JPF_java_net_Socket.updateSocketHashForDataWrite(env, socketRef, data);
  }

  @MJI
  public void write___3BII__V (MJIEnv env, int objRef, int bufferRef, int off, int len) {
    int socketRef = env.getElementInfo(objRef).getReferenceField("socket");
    Connection conn = connections.getConnection(socketRef);
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
      // **KEY CHANGE: ALWAYS update hash for all bytes first, regardless of connection**
      byte[] values = env.getByteArrayObject(bufferRef);
      for(int i = off; i < off + len; i++) {
        updateSocketHash(env, socketRef, values[i] & 0xFF);
      }

      // Only proceed with actual I/O operations if connection exists
      if(conn == null) {
        return; // Hash updated successfully, but no network I/O
      }

      if(isConnBroken(env, objRef, conn)) {
        return;
      }

      // Proceed with actual network I/O operations
      int reader = getOtherEnd(conn, socketRef);

      if(JPF_java_net_SocketInputStream.isReadBufferEmpty(conn, reader)) {
        unblockRead(env, conn, socketRef);
      }

      writeByteArray(env, bufferRef, conn, socketRef, off, len);
    }
  }



  /**
   * Writing on a closed or terminated connection requires throwing an exception
   * 
   * @return true if there is no exception, OW false
   */
  protected boolean isConnBroken(MJIEnv env, int objRef, Connection conn) {
    // Add null check for connection - DON'T throw exception here
    if (conn == null) {
      // Return true to indicate broken connection, but don't throw exception
      // This avoids JPF serialization issues
      return true;
    }

    if(conn.isClosed()) {
      String msg;
      if(JPF_java_net_SocketInputStream.isThisEndClosed(env, objRef)) {
        msg = "Socket closed";
      } else {
        msg = "Broken pipe";
      }
      env.throwException("java.net.SocketException", msg);
      return true;
    }

    return false;
  }

  
  // unblocks a read which was waiting on an empty buffer
  protected void unblockRead(MJIEnv env, Connection conn, int endpoint) {
    ThreadInfo ti = env.getThreadInfo();
    int blockedReader = getOtherEnd(conn, endpoint);
    
    ElementInfo ei =  env.getElementInfo(blockedReader);
    
    // TODO - we will get rid of this once we handle shutdown semantics
    if(ei==null) {
      return;
    }
    
    int tiRef = ei.getReferenceField("waitingThread");
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
  protected void writeByte(int value, Connection conn, int endpoint) {
    if(conn.isClientEndSocket(endpoint)) {
      conn.clientWrite((byte)value);
    } else {
      conn.serverWrite((byte)value);
    }
  }
  
  // writes an array of byte, represented by dataRef, into this buffer
// writes an array of byte, represented by dataRef, into this buffer
  protected void writeByteArray(MJIEnv env, int arrValue, Connection conn, int endpoint, int off, int len) {
    byte[] values = env.getByteArrayObject(arrValue);

    // REMOVE hash updates from here - they're already done in the calling method
    for(int i = off; i < off + len; i++) {
      writeByte(values[i], conn, endpoint);  // Just write, no hash update
    }
  }
  
  protected static int getOtherEnd(Connection conn, int endpoint) {
    if(conn.isClientEndSocket(endpoint)) {
      return conn.getServerEndSocket();
    } else {
      return conn.getClientEndSocket();
    }
  }
  
  protected String[] getInjectedExceptions() {
    
    if(Scheduler.failure_injection) {
      String[] exceptions = {Scheduler.IO_EXCEPTION};
      return exceptions;
    }
    
    return Scheduler.EMPTY;
  }
  
  protected static void printWriter(Connection conn, int endpoint) {
    String result;
    if(conn.isClientEndSocket(endpoint)) {
      result = "Client Writing";
    } else {
      result = "Server Writing";
    }
  }
}
