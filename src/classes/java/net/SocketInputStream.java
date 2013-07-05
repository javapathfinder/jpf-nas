package java.net;

import java.io.IOException;
import java.io.InputStream;

/** 
 * @author Nastaran Shafiei
 */
public class SocketInputStream extends InputStream {
  
 // private Buffer buffer;
  private Socket socket;

  public SocketInputStream(Socket socket) {
    this.socket = socket;
   // this.buffer = new Buffer();
  }

  @Override
  public native int read () throws IOException;

  @Override
  public native int read (byte[] b) throws IOException;
}
