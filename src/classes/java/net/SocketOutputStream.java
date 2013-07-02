package java.net;

import java.io.IOException;
import java.io.OutputStream;

/** 
 * @author Nastaran Shafiei
 */
public class SocketOutputStream extends OutputStream {

  private Socket socket = null;

  private Buffer buffer;
  
  public SocketOutputStream(Socket socket) {
    this.socket = socket;
    buffer = new Buffer();
  }

  SocketOutputStream(Socket socket, Buffer buffer) {
    this.socket = socket;
    this.buffer = buffer;
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
