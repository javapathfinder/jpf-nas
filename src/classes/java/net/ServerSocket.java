package java.net;

import java.io.IOException;

/** 
 * Model class for java.net.ServerSocket
 * 
 * @author Nastaran Shafiei
 */
public class ServerSocket implements java.io.Closeable {

  private static ServerSocket[] waitingSockets = new ServerSocket[0];

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
    SocketImpl.checkPort(port);
    impl = new SocketImpl();
    impl.setServerSocket(this);
    impl.bind(port);
  }

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

  public Socket accept () throws IOException {
    System.out.println("\n\n\nServer> accepting ... ");
    waitingSockets = addToWaitingSockets(this);

    tempSocket = null;
    
    System.out.println("Server> waiting ... ");
    acceptConnectionRequest();

    Socket s = new Socket();
    s.shareIOBuffers(tempSocket);

    System.out.println("Server> accepted connection!!!!" );
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


  @Override
  public synchronized void close() throws IOException {
    synchronized(closeLock) {
      closed = true;
    }
  }
}
