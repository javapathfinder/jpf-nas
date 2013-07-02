package nas.java.net;

import gov.nasa.jpf.annotation.MJI;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.NativePeer;

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
    int arrRef = env.getElementInfo(objRef).getReferenceField("data");
    byte[] data = env.getByteArrayObject(arrRef);
    int ret = data[0];
    for(int i=0; i<data.length-1; i++) {
      env.getModifiableElementInfo(arrRef).setByteElement(i, data[i+1]);
    }
    env.getModifiableElementInfo(arrRef).setByteElement(data.length-1, DEFAULT_VALUE);

    return ret;
  }

  @MJI
  public int read___3B__I (MJIEnv env, int objRef, int desArrRef) {
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

  // shifts array elements by n to the left
  private void shiftElements(MJIEnv env, int arrRef, int n) {
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
  
  @MJI
  public void write__I__V (MJIEnv env, int objRef, int value) {
    boolean isClosed = env.getElementInfo(objRef).getBooleanField("closed");

    // the socket at the other end has been close, so throw IOException
    // TODO - I think we need transition break upon closing
    if(isClosed) {
     // env.throwException("java.io.IOException");
     // return;
    }

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

  @MJI
  public void write___3B__V (MJIEnv env, int objRef, int dataRef) {
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
}
