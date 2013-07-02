package java.net;

import java.io.IOException;
import java.io.InputStream;

/** 
 * @author Nastaran Shafiei
 */
public class SocketInputStream extends InputStream {

  private Socket socket = null;
  
  private Buffer buffer;

  public SocketInputStream(Socket socket) {
    this.socket = socket;
    this.buffer = new Buffer();
  }

  SocketInputStream(Socket socket, Buffer buffer) {
    this.socket = socket;
    this.buffer = buffer;
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
