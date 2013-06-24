package java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 
 * Model class for java.net.Socket
 * 
 * @author Nastaran Shafiei
 */
public class Socket implements java.io.Closeable {

  private SocketInputStream input = null;
  private SocketOutputStream output = null;
  
  /**
   * The implementation of this Socket.
   */
  SocketImpl impl; 
  
  /**
   * Various states of this socket.
   */
  private boolean bound = false;
  private boolean connected = false;
  private boolean closed = false;
  private Object closeLock = new Object();

  public Socket(String host, int port) throws UnknownHostException, IOException {
    SocketImpl.checkPort(port);
    impl = new SocketImpl();
    impl.setSocket(this);
    impl.bind(-1);
    impl.remoteHost = host;
    impl.port = port;
    connect(host, port);
  }

  Socket() {
  }

  void setBound() {
    bound = true;
  }

  public InputStream getInputStream() throws IOException {
    return input;
  }

  public OutputStream getOutputStream() throws IOException {
    return output;
  }
  
  /**
   * Returns the closed state of the socket.
   */
  public boolean isClosed() {
      synchronized(closeLock) {
        return closed;
      }
  }

  private native void closeInputStreamBuffer();

  @Override
  public synchronized void close() throws IOException {
    synchronized(closeLock) {
      closeInputStreamBuffer();
      closed = true;
    }
  }

  private native void sendConnectionRequest(String host, int port);

  public void connect(String host, int port) throws IOException {
    System.out.println("\n\n\nClient> Connecting ... ");
    this.initIOStream();
    sendConnectionRequest(host, port);
  }

  private void initIOStream() {
    this.input = new SocketInputStream(this);
    this.output = new SocketOutputStream(this);
  }

  void shareIOBuffers(Socket s) {
    this.input = new SocketInputStream(this, s.output.getBuffer());
    this.output = new SocketOutputStream(this, s.input.getBuffer());
  }

  private native Thread connectSocket(String host, int port) throws IOException;
}
