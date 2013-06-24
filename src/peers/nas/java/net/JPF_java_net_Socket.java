package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.ChoiceGenerator;
import gov.nasa.jpf.vm.ClassInfo;
import gov.nasa.jpf.vm.ClassLoaderInfo;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.MultiProcessVM;
import gov.nasa.jpf.vm.NasSchedulingChoices;
import gov.nasa.jpf.vm.NativePeer;
import gov.nasa.jpf.vm.SystemState;
import gov.nasa.jpf.vm.ThreadInfo;

/**
 * The native peer class for our java.net.Socket
 * 
 * @author Nastaran Shafiei
 */
public class JPF_java_net_Socket extends NativePeer {

  protected ClassLoaderInfo getHostSystemClassLoaderInfo(MJIEnv env, String host) {
    ApplicationContext[] appContext = MultiProcessVM.getVM().getApplicationContexts();
    for(int i=0; i<appContext.length; i++) {
      if(appContext[i].getHost().equals(host)) {
        return appContext[i].getSystemClassLoader();
      }
    }
    // TODO - should throw exception! which exception?
    env.throwException("java.net.UnknownHostException");
    return null;
  }

  public int getRemoteServer (MJIEnv env, int objRef, int hostRef, int port) {
    int serverRef = MJIEnv.NULL;
    String host = env.getStringObject(hostRef);
    ClassLoaderInfo cl = getHostSystemClassLoaderInfo(env, host);

    // couldn't find the host
    if(cl == null) {
      return MJIEnv.NULL;
    }

    ClassInfo ci = cl.getResolvedClassInfo("java.net.ServerSocket");
    
    if(ci.isRegistered()) {
      ElementInfo ei = ci.getStaticElementInfo();
      int arrRef = ei.getReferenceField("waitingSockets");
      System.out.println();
      int[] waitingSockets = env.getReferenceArrayObject(arrRef);

      for(int i=0; i<waitingSockets.length; i++) {
        int implRef = env.getElementInfo(waitingSockets[i]).getReferenceField("impl");
        int serverPort = env.getElementInfo(implRef).getIntField("localPort");
        if(serverPort==port) {
          serverRef = waitingSockets[i];
        }
      }
    }

    if(serverRef != MJIEnv.NULL) {
      env.getModifiableElementInfo(serverRef).setReferenceField("tempSocket", objRef);
    } else {
      env.throwException("java.io.IOException");
    }

    return serverRef;
  }

  @MJI
  public void sendConnectionRequest__Ljava_lang_String_2I__V (MJIEnv env, int socketRef, int hostRef, int port) {
    int serverRef = getRemoteServer(env, socketRef, hostRef, port);

    System.out.println("Client> sending request ...");
    if(serverRef == MJIEnv.NULL) {
      return;
    }
    
    ThreadInfo ti = env.getThreadInfo();
   
    if (!ti.isFirstStepInsn()){
      int tiRef = env.getElementInfo(serverRef).getReferenceField("waitingThread");
      ThreadInfo tiAccept = env.getThreadInfoForObjRef(tiRef);    
      if (tiAccept == null || tiAccept.isTerminated()){
        return;
      }      
      
      SystemState ss = env.getSystemState();
      int lockRef = env.getReferenceField( serverRef, "acceptLock");
      ElementInfo lock = env.getModifiableElementInfo(lockRef);

      if (tiAccept.getLockObject() == lock){
        // note that 'permit' is only used in park/unpark, so there never is more than
        // one waiter, which immediately becomes runnable again because it doesn't hold a lock
        // (park is a lockfree wait). unpark() therefore has to be a right mover
        // and we have to register a ThreadCG here
        
        env.getElementInfo(serverRef).setReferenceField("tempSocket",socketRef);
        lock.notifies(ss, ti, false);

        //ChoiceGenerator<?> cg = env.getSchedulerFactory().createUnparkCG(tiAccept);
        ChoiceGenerator<?> cg = NasSchedulingChoices.createConnectCG(ti);
        if (cg != null){
          ss.setNextChoiceGenerator(cg);
          env.repeatInvocation();
        }
        
      } else {
       // eiPermit.setBooleanField("blockPark", false);
      }      
    }
  }

  @MJI
  public void closeInputStreamBuffer____V (MJIEnv env, int serverSocketRef) {
    int inputStreamRef = env.getElementInfo(serverSocketRef).getReferenceField("input");
    int bufferRef = env.getElementInfo(inputStreamRef).getReferenceField("buffer");
    env.getModifiableElementInfo(bufferRef).setBooleanField("closed", true);
  }
}
