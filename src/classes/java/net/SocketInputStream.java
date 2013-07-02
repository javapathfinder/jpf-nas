package java.net;

import java.io.IOException;
import java.io.InputStream;

/** 
 * @author Nastaran Shafiei
 */
public class SocketInputStream extends InputStream {
  
  private Buffer buffer;

  public SocketInputStream() {
    this.buffer = new Buffer();
  }

  @Override
  public int read () throws IOException {
    return buffer.read();
  }

  @Override
  public int read (byte[] b) throws IOException {
    return buffer.read(b);
  }

  Buffer getBuffer() {
    return this.buffer;
  }
}
