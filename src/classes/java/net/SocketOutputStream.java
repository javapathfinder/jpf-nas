package java.net;

import java.io.IOException;
import java.io.OutputStream;

/** 
 * @author Nastaran Shafiei
 */
public class SocketOutputStream extends OutputStream {

  //private Buffer buffer;
  private Socket socket;
  
  public SocketOutputStream(Socket socket) {
    this.socket = socket;
  //  this.buffer = new Buffer();
  }

  @Override
  public native void write (int b) throws IOException;

  @Override
  public native void write(byte b[]) throws IOException;
}
