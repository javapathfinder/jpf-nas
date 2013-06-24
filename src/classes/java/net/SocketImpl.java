package java.net;

/** 
 * @author Nastaran Shafiei
 */
public class SocketImpl {
  /**
   * The actual Socket objects
   */
  Socket socket = null;
  ServerSocket serverSocket = null;

  /**
   * The port number on the remote host to which this socket is connected.
   */
  protected int port;

  /**
   * The local port, set to a non-zero number, on which the server listen 
   * for connection.
   */
  protected int localPort;


  /**
   * This is the remote host
   */
  protected String remoteHost;

  protected int getLocalPort() {
    return this.localPort;
  }

  static int checkPort(int port) {
    if (port < 0 || port > 0xFFFF)
        throw new IllegalArgumentException("port out of range:" + port);
    return port;
  }

  protected synchronized void bind(int port) {
    this.localPort = port;

    if(socket!=null) {
      socket.setBound();
    } 

    if(serverSocket!=null) {
      serverSocket.setBound();
    }
  }

  protected void setServerSocket(ServerSocket serverSocket) {
    this.serverSocket = serverSocket;
  }

  protected void setSocket(Socket socket) {
    this.socket = socket;
  }
}
