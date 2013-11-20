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
  
  // only for socket objects returned by ServerSocket.accept() is natively set to
  // non-null value which the client
  final private Socket clientEnd = null;
  
  /**
   * The implementation of this Socket.
   */
  SocketImpl impl; 
  
  /**
   * Various states of this socket.
   */
  private boolean bound = false;
  private boolean connected = false;
  private Object closeLock = new Object();
  private boolean closed = false;

  private Object lock = new Object();
  private Thread waitingThread;

  public Socket() {
    this.setIOStream();
  }

  public Socket(String host, int port) throws UnknownHostException, IOException {
    SocketImpl.checkPort(port);
    impl = new SocketImpl();
    impl.setSocket(this);
    impl.bind(-1);
    impl.remoteHost = host;
    impl.port = port;
    this.setIOStream();
    connect(host, port);
  }

  private void setIOStream() {
    this.input = new SocketInputStream(this);
    this.output = new SocketOutputStream(this);
  }
  
  public Socket(InetAddress address, int port) throws UnknownHostException, IOException {
    this(address.getHostName(), port);
  }

  public native void connect(String host, int port) throws IOException;

  // TODO: for timeout, look at wait() implementation
  public native void connect(SocketAddress endpoint, int timeout) throws IOException;
  
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
    return this.closed;
  }
  
  // TODO: Any thread currently blocked in an I/O operation upon this socket 
  //       will throw a SocketException.
  // TODO: Throws IOException, if an I/O error occurs when closing the socket
  @Override
  public native void close() throws IOException;
  
  private int timeout;
  public void setSoTimeout(int timeout) {
    this.timeout = timeout;
  }
}
