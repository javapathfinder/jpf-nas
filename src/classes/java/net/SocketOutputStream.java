package java.net;

import java.io.IOException;
import java.io.OutputStream;

/** 
 * @author Nastaran Shafiei
 */
public class SocketOutputStream extends OutputStream {

  //private Buffer buffer;
  private final Socket socket;
  
  public SocketOutputStream(Socket socket) {
    this.socket = socket;
  //  this.buffer = new Buffer();
  }

  @Override
  public native void write (int b) throws IOException;

  @Override
  public void write(byte b[]) throws IOException {
    write(b, 0, b.length);
  }
  
  @Override
  public native void write(byte b[], int off, int len) throws IOException;
  
  private boolean closed = false;
  
  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    socket.close();
  }
}
