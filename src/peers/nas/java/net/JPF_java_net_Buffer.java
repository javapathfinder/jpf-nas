package nas.java.net;

import nas.java.net.choice.NasSchedulingChoices;
import nas.java.net.connection.Connections;
import nas.java.net.connection.Connections.Connection;
import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NativePeer;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;

/**
 * The native peer class for our java.net.Buffer which encapsulates the communication
 * channel between sockets
 * 
 * @author Nastaran Shafiei
 */
public class JPF_java_net_Buffer extends NativePeer {

  public static byte DEFAULT_VALUE = -1;
  
  @MJI
  public int read____I (MJIEnv env, int objRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now do the read()
      return readByte(env, objRef);
    } else {
      if(isEmpty(env, objRef)) {
        blockRead(env, objRef);
        env.repeatInvocation(); // re-execute needed once server gets interrupted
        return -1;
      } else {
        return readByte(env, objRef);
      }
    }
  }
  
  @MJI
  public int read___3B__I (MJIEnv env, int objRef, int desArrRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    if(ti.isFirstStepInsn()) { // re-execute after it got unblock, now do the read()
      // TODO - explore other cases! maybe it has got interrupted
      return readByteArray(env, objRef, desArrRef);
    } else {
      if(isEmpty(env, objRef)) {
        blockRead(env, objRef);
        env.repeatInvocation(); // re-execute needed once server gets interrupted
        return -1;
      } else {
        return readByteArray(env, objRef, desArrRef);
      }
    }
  }
  
  // makes the current thread get block on an empty buffer
  protected void blockRead(MJIEnv env, int bufferRef) {
    ThreadInfo ti = env.getThreadInfo();
    
    int lock = env.getReferenceField( bufferRef, "lock");
    ElementInfo ei = env.getModifiableElementInfo(lock);
    env.getModifiableElementInfo(bufferRef).setReferenceField("waitingThread", ti.getThreadObjectRef());
    
    ei.wait(ti, 0, false);
    
    assert ti.isWaiting();
    
    ChoiceGenerator<?> cg = NasSchedulingChoices.createBlockingReadCG(ti);
    env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking InputStream.read()");
  }
  
  // reads a single byte and returns its value
  protected int readByte(MJIEnv env, int objRef) {
    int arrRef = env.getElementInfo(objRef).getReferenceField("data");
    byte[] data = env.getByteArrayObject(arrRef);
    
    int ret = data[0];
    for(int i=0; i<data.length-1; i++) {
      env.getModifiableElementInfo(arrRef).setByteElement(i, data[i+1]);
    }
    env.getModifiableElementInfo(arrRef).setByteElement(data.length-1, DEFAULT_VALUE);
    
    return ret;
  }
  
  // reads "some" bytes into a given array, represented by desArrRef and returns the
  // number of byte which are read
  protected int readByteArray(MJIEnv env, int objRef, int desArrRef) {
    int arrRef = env.getElementInfo(objRef).getReferenceField("data");
    byte[] data = env.getByteArrayObject(arrRef);
    int len = env.getByteArrayObject(desArrRef).length;

    int minLen = Math.min(len, data.length);
    for(int i=0; i<minLen; i++) {
      env.getModifiableElementInfo(desArrRef).setByteElement(i, data[i]);
    }

    shiftElements(env, arrRef, minLen);
    return minLen;
  }
  
  @MJI
  public void write__I__V (MJIEnv env, int objRef, int value) {
    boolean isClosed = env.getElementInfo(objRef).getBooleanField("closed");

    // the socket at the other end has been close, so throw IOException
    // TODO - I think we need transition break upon closing
    if(isClosed) {
     // env.throwException("java.io.IOException");
     // return;
    }
    
    // if it is empty, then there might be a read() waiting for someone to write 
    if(isEmpty(env, objRef)) {
      unblockRead(env, objRef);
    }
    
    writeByte(env, objRef, value);
  }
  
  @MJI
  public void write___3B__V (MJIEnv env, int objRef, int dataRef) {
    boolean isClosed = env.getElementInfo(objRef).getBooleanField("closed");

    // the socket at the other end has been close, so throw IOException
    // TODO - I think we need transition break upon closing
    if(isClosed) {
     // env.throwException("java.io.IOException");
     // return;
    }
    
    // if it is empty, then there might be a read() waiting for someone to write 
    if(isEmpty(env, objRef)) {
      unblockRead(env, objRef);
    }
    
    writeByteArray(env, objRef, dataRef);
  }
  
  // shifts the elements of an array represented by arrRef by n to the left
  protected void shiftElements(MJIEnv env, int arrRef, int n) {
    byte[] data = env.getByteArrayObject(arrRef);
    int len = data.length;

    for(int i=0; i<len; i++) {
      if(i<(len-n)) {
        env.getModifiableElementInfo(arrRef).setByteElement(i, data[i+n-1]);
      } else {
        env.getModifiableElementInfo(arrRef).setByteElement(i, DEFAULT_VALUE);
      }
    }
  }
  
  // unblocks a read which was waiting on an empty buffer
  protected void unblockRead(MJIEnv env, int bufferRef) {
    ThreadInfo ti = env.getThreadInfo();

    int tiRef = env.getElementInfo(bufferRef).getReferenceField("waitingThread");
    ThreadInfo tiRead = env.getThreadInfoForObjRef(tiRef);    
    if (tiRead == null || tiRead.isTerminated()){
      System.out.println("returning!! " + tiRead);
      return;
    }
    
    SystemState ss = env.getSystemState();
    int lockRef = env.getReferenceField( bufferRef, "lock");
    ElementInfo lock = env.getModifiableElementInfo(lockRef);
    
    System.out.println("same lock? " + (tiRead.getLockObject() == lock));
    if (tiRead.getLockObject() == lock){
      lock.notifies(ss, ti, false);
      
      ChoiceGenerator<?> cg = NasSchedulingChoices.createWriteCG(ti);
      if (cg != null){
        ss.setNextChoiceGenerator(cg);
        // env.repeatInvocation(); - no need to re-execute
      }
      System.out.println("Server> done unblocking the read ... read.thread.isWaiting: " + tiRead.isWaiting());
    }
  }
  
  // writes a single byte value into this buffer
  protected void writeByte(MJIEnv env, int objRef, int value) {
    int arrRef = env.getElementInfo(objRef).getReferenceField("data");
    byte[] data = env.getByteArrayObject(arrRef);
    int i=0;
    while(data[i]!=DEFAULT_VALUE) {
      i++;
    }

    if(i<data.length) {
      env.getModifiableElementInfo(arrRef).setByteElement(i, (byte)value);
    } else {
      // TODO - buffer is full!
    }
  }
  
  // writes an array of byte, represented by dataRef, into this buffer
  protected void writeByteArray(MJIEnv env, int objRef, int dataRef) {
    byte[] b = env.getByteArrayObject(dataRef);
    int arrRef = env.getElementInfo(objRef).getReferenceField("data");
    byte[] data = env.getByteArrayObject(arrRef);

    int i=0;
    while(data[i]!=DEFAULT_VALUE) {
      i++;
    }

    int minLen = Math.min(b.length, data.length-i);
    for(int j=i; j<minLen; j++) {
      env.getModifiableElementInfo(arrRef).setByteElement(j, b[j-i]);
    }
  }
  
  // checks if this buffer is empty
  protected boolean isEmpty(MJIEnv env, int objRef) {
    int arrRef = env.getElementInfo(objRef).getReferenceField("data");
    byte[] data = env.getByteArrayObject(arrRef);
    for(int i=0; i<data.length; i++) {
      if(data[i]!=DEFAULT_VALUE) {
        return false;
      }
    }
    return true;
  }
  
  //checks if this buffer is full
  protected boolean isFull(MJIEnv env, int objRef) {
    int arrRef = env.getElementInfo(objRef).getReferenceField("data");
    byte[] data = env.getByteArrayObject(arrRef);
    for(int i=0; i<data.length; i++) {
      if(data[i]==DEFAULT_VALUE) {
        return false;
      }
    }
    return true;
  }
}
