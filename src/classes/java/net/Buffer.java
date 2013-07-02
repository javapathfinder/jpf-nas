package java.net;

import java.io.IOException;

/**
 * This class encapsulates the communication channel for client/server which
 * is used to transmit continuous stream of bytes
 * 
 * NOTE: This does not correspond to any standard class form the Java library. 
 * 
 * @author Nastaran Shafiei
 */
class Buffer {
  static int DEFAUT_SIZE = 10; //2KB 
  static byte DEFAULT_VALUE = -1;
  byte[] data;

  private boolean closed = false;

  public Buffer() {
    data = new byte[DEFAUT_SIZE];
    for(int i=0; i<DEFAUT_SIZE; i++) {
      data[i] = DEFAULT_VALUE;
    }
  }

  native int read() throws IOException;

  native int read(byte[] b) throws IOException;
  
  native void write(int b) throws IOException;

  native void write(byte[] b) throws IOException;
}
