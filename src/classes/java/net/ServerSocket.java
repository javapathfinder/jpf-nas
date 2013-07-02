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
   *       the local port on which this socket listen for connections
   */
  public ServerSocket (int port) throws IOException {
   // CheckForAddressAlreadyInUse(port);
    SocketImpl.checkPort(port);
    impl = new SocketImpl();
    impl.setServerSocket(this);
    impl.bind(port);
  }

//  public ServerSocket (int port, int i) throws IOException {
//    
//  }

  private native void CheckForAddressAlreadyInUse(int port);

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


  private Object acceptLock = new Object();

  private Socket tempSocket;
  private Thread waitingThread;

  private native void acceptConnectionRequest();

  private native Socket getConnectedClientSocket(); 
  
  /**
   * Using this server accepts the connection request and receives a new active
   * socket which represents its end of the connection
   */
  public Socket accept () throws IOException {
    System.out.println("Server> accepting ... ");

    tempSocket = null;
    
    System.out.println("Server> waiting ... ");
    acceptConnectionRequest();
    System.out.println("Server> accepted connection!!!!" );

    Socket s = new Socket();
    s.shareIOBuffers(tempSocket);
    System.out.println("Server> done accepting request!");
    return s;
  }

  /**
   * Returns the closed state of the socket.
   */
  public boolean isClosed() {
      synchronized(closeLock) {
        return closed;
      }
  }

  private native void closeConnections();
  
  @Override
  public synchronized void close() throws IOException {
    synchronized(closeLock) {
      closed = true;
      closeConnections();
    }
  }
}
