package java.net;

import java.io.IOException;
import java.io.OutputStream;

/** 
 * @author Nastaran Shafiei
 */
public class SocketOutputStream extends OutputStream {

  private Buffer buffer;
  
  public SocketOutputStream() {
    buffer = new Buffer();
  }

  @Override
  public void write (int b) throws IOException {
    buffer.write(b); 
  }

  public void write(byte b[]) throws IOException {
    buffer.write(b);
  }

  Buffer getBuffer() {
    return this.buffer;
  }  
}
