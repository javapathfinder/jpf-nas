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

  private Object clientLock = new Object();
  private Thread waitingThread;
  private Socket endpoint;

  public Socket() {
    initIOStream();
  }

  public Socket(String host, int port) throws UnknownHostException, IOException {
    SocketImpl.checkPort(port);
    impl = new SocketImpl();
    impl.setSocket(this);
    impl.bind(-1);
    impl.remoteHost = host;
    impl.port = port;
    this.initIOStream();
    connect(host, port);
  }

  public Socket(InetAddress address, int port) throws UnknownHostException, IOException {
    this(address.getHostName(), port);
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

  private native void closeConnection();

  @Override
  public synchronized void close() throws IOException {
    synchronized(closeLock) {
      closeConnection();
      closed = true;
    }
  }

  public native void connect(String host, int port);

  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    
  }

  private void initIOStream() {
    this.input = new SocketInputStream(this);
    this.output = new SocketOutputStream(this);
  }

  private native Thread connectSocket(String host, int port) throws IOException;
}
