package java.net;

import java.io.IOException;

/**
 * This class is used to encapsulate the communication channel for client/server
 * 
 * NOTE: This does not correspond to any standard class form the Java library. 
 * 
 * @author Nastaran Shafiei
 */
class Buffer {
  static byte DEFAULT_VALUE = -1;
  byte[] data;

  private boolean closed = false;

  public Buffer() {
    data = new byte[100];
    for(int i=0; i<100; i++) {
      data[i] = DEFAULT_VALUE;
    }
  }

  public native int read () throws IOException;

  public native void write (int b) throws IOException;
}
