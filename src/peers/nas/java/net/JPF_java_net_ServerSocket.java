package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NasSchedulingChoices;
import gov.nasa.jpf.vm.NativePeer;
import gov.nasa.jpf.vm.SystemClassLoaderInfo;
import gov.nasa.jpf.vm.ThreadInfo;

/**
 * The native peer class for our java.net.ServerSocket
 * 
 * @author Nastaran Shafiei
 */
public class JPF_java_net_ServerSocket extends NativePeer {

  @MJI
  public int addToWaitingSockets__Ljava_net_ServerSocket_2___3Ljava_net_ServerSocket_2 (MJIEnv env, int serverSocketRef, int socket) {
    ClassInfo ci = SystemClassLoaderInfo.getSystemResolvedClassInfo("java.net.ServerSocket");
    ElementInfo ei = ci.getStaticElementInfo();
    
    int arrRef = ei.getReferenceField("waitingSockets");
    
    int[] waitingSockets = env.getReferenceArrayObject(arrRef);

    // check if it is already among the waiting sockets
    for(int i=0; i<waitingSockets.length; i++) {
      if(waitingSockets[i]==socket) {
        return arrRef;
      }
    }

    int newArrRef = env.newObjectArray("java.net.ServerSocket", waitingSockets.length+1);
    
    for(int i=0; i<waitingSockets.length; i++) {
      env.getModifiableElementInfo(newArrRef).setReferenceElement(i, waitingSockets[i]);
    }
    env.getModifiableElementInfo(newArrRef).setReferenceElement(waitingSockets.length, serverSocketRef);

    return newArrRef;
  }

  // this is a specialized, native wait that does not require a lock, and that can
  // be turned off by a preceding unpark() call (which is not accumulative)
  // park can be interrupted, but it doesn't throw an InterruptedException, and it doesn't clear the status
  // it can only be called from the current (parking) thread
  @MJI
  public void acceptConnectionRequest____V (MJIEnv env, int serverSocketRef) {
    ThreadInfo ti = env.getThreadInfo();
    int lock = env.getReferenceField( serverSocketRef, "acceptLock");
    ElementInfo ei = env.getModifiableElementInfo(lock);

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
    } else { // first time
      env.getElementInfo(serverSocketRef).setReferenceField("waitingThread", ti.getThreadObjectRef());
      ei.wait(ti, 0, false);

      assert ti.isWaiting();

      // note we pass in the timeout value, since this might determine the type of CG that is created
      ChoiceGenerator<?> cg = NasSchedulingChoices.createAcceptCG(ti);
      env.setMandatoryNextChoiceGenerator(cg, "no CG on blocking ServerSocket.accept()");
      env.repeatInvocation();
    }
  }
}
