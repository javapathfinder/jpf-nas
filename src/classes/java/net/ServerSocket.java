package java.net;

import java.io.IOException;

/**
 * Model class for java.net.ServerSocket
 * 
 * @author Nastaran Shafiei
 */
public class ServerSocket implements java.io.Closeable {

  /**
   * The implementation of this Socket.
   */
  private SocketImpl impl;

  /**
   * Various states of this socket.
   */
  private boolean bound = false;

  private boolean closed = false;

  private Object closeLock = new Object();

  /**
   * This creates a 'bound' socket which is ready for ServerSocket.accept() to
   * be called.
   * 
   * @param port
   *          the local port on which this socket listen for connections
   */
  public ServerSocket (int port) throws IOException {
    CheckForAddressAlreadyInUse(port);
    SocketImpl.checkPort(port);
    impl = new SocketImpl();
    impl.setServerSocket(this);
    impl.bind(port);
  }

  private native void CheckForAddressAlreadyInUse (int port);

  void setBound () {
    this.bound = true;
  }

  /**
   * This creates an 'unbound' socket which must be bound using
   * ServerSocket.bind() before ServerSocket.accept() is invoked
   */
  public ServerSocket () throws IOException {

  }

  private native ServerSocket[] addToWaitingSockets (ServerSocket socket);

  private Object lock = new Object();

  private Socket acceptedSocket;

  private Thread waitingThread;

  private native void accept0 ();

  /**
   * Using this server accepts the connection request and receives a new active
   * socket which represents its end of the connection
   */
  public Socket accept () throws IOException {
    if (isClosed()) {
      throw new SocketException("Socket is closed");
    } 
    // TODO: check for the "unbound" state
    
    // The IO buffers of this socket are shared natively with the client socket
    // at the other end
    acceptedSocket = new Socket();
    accept0();

    return acceptedSocket;
  }

  /**
   * Returns the closed state of the socket.
   */
  public boolean isClosed () {
    return this.closed;
  }

  // TODO: Any thread currently blocked in accept() will throw a SocketException
  // TODO: Throws IOException, if an I/O error occurs when closing the socket
  @Override
  public native synchronized void close ();
  
  private int timeout;
  public void setSoTimeout(int timeout) {
    this.timeout = timeout;
  }
}
