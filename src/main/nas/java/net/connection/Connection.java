package nas.java.net.connection;

import gov.nasa.jpf.util.ArrayByteQueue;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.MJIEnv;

public class Connection  implements Cloneable {
  
  public enum State {
    PENDING,
    ESTABLISHED,
    CLOSED,
    TERMINATED
  };

  String serverHost;
  State state;
  int port;
  
  // ServerSocket Object
  int serverPassiveSocket;
  int serverEndSocket;
  ApplicationContext serverApp;
  
  int clientEndSocket;
  ApplicationContext clientApp;

  // communication buffers
  ArrayByteQueue server2client; // server out and client in
  ArrayByteQueue client2server; // client out and server in
  
  public Connection(int port) {
    this.port = port;
    this.state = State.PENDING;
    
    this.serverPassiveSocket = MJIEnv.NULL;
    this.serverEndSocket = MJIEnv.NULL;
    this.clientEndSocket = MJIEnv.NULL;
    
    server2client = new ArrayByteQueue();
    client2server = new ArrayByteQueue();
  }
  
  public int getPort() {
    return this.port;
  }

  public int getServerPassiveSocket() {
    return this.serverPassiveSocket;
  }
  
  public int getServerEndSocket() {
    return this.serverEndSocket;
  }

  public int getClientEndSocket() {
    return this.clientEndSocket;
  }
  
  public Object clone() {
    Connection clone = null;

    try {
      clone = (Connection)super.clone();
      clone.client2server = (ArrayByteQueue)this.client2server.clone();
      clone.server2client = (ArrayByteQueue)this.server2client.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }

    return clone;
  }

  protected void setServerInfo(int serverPassiveSocket, int serverEndSocket, ApplicationContext serverApp) {
    this.serverPassiveSocket = serverPassiveSocket;
    this.serverEndSocket = serverEndSocket;
    
    this.serverApp = serverApp;
    this.serverHost = serverApp.getHost();

    if(this.hasClient()) {
      this.establish();
    }
  }
  
  protected void setClientInfo(int client, ApplicationContext clientApp, String serverHost) {
    this.clientEndSocket = client;
    this.clientApp = clientApp;
    this.serverHost = serverHost;

    if(this.hasServer()) {
      this.establish();
    }
  }
  
  private void setServerEndSocket(int serverEndSocket) {
    this.serverEndSocket = serverEndSocket;
  }
  
  public void establishedConnWithServer(int serverPassiverSocket, int serverEndSocket, ApplicationContext serverApp) {
    if(this.hasClient()) {
      this.setServerInfo(serverPassiverSocket, serverEndSocket, serverApp);
    } else {
      throw new ConnectionException();
    }
  }
  
  public void establishedConnWithClient(int clientEndSocket, ApplicationContext clientApp, String host, int serverEndSocket) {
    if(this.hasServer()) {
      this.setClientInfo(clientEndSocket, clientApp, host);
      this.setServerEndSocket(serverEndSocket);
    } else {
      throw new ConnectionException();
    }
  }
  
  public String getServerHost() {
    return this.serverHost;
  }

  public String getClientHost() {
    return this.clientApp.getHost();
  }

  public boolean hasServer() {
    return (this.serverPassiveSocket!=MJIEnv.NULL);
  }

  public boolean hasClient() {
    return (this.clientEndSocket!=MJIEnv.NULL);
  }
  
  public boolean isClientEndSocket(int socket) {
    if(this.clientEndSocket == socket) {
      return true;
    } else if(this.serverEndSocket == socket){
      return false;
    } else {
      throw new ConnectionException("the socket does not belong to this connection!");
    }
  }

  // check if the given socket object is an end-point of this connection
  public boolean isConnectionEndpoint(int socket) {
    return (this.clientEndSocket==socket || this.serverEndSocket==socket);
  }

  protected void establish() {
    this.state = State.ESTABLISHED;
  }

  public boolean isEstablished() {
    return(this.state==State.ESTABLISHED);
  }

  protected void close() {
    this.state = State.CLOSED;
  }

  public boolean isClosed() {
    return(this.state==State.CLOSED);
  }
  
  protected void terminate() {
    this.state = State.TERMINATED;
  }

  public boolean isTerminated() {
    return(this.state==State.TERMINATED);
  }
  
  public boolean isPending() {
    return(!isTerminated() && !(this.hasServer() && this.hasClient()));
  }
  
  public String toString() {
    String result = "\nserverPassiveSocket: " + this.serverPassiveSocket + " serverEnd:" + this.serverEndSocket +" (host:" + this.serverHost +")" + " <---port:" + 
      this.port + "--->" + " clientEnd:" + this.clientEndSocket + " ["+this.state+"]\n";
    result += "clinet>=>server buffer: " + client2server + "\n";
    result += "server>=>client buffer: " + server2client + "\n";
    return result;
  }
  
  public int serverRead() {
    // server reading ...
    return client2server.poll().byteValue();
  }
  
  public int clientRead() {
    // client reading ...
    return server2client.poll().byteValue();
  }
  
  public void serverWrite(byte value) {
    // server writing ...
    server2client.add(value);
  }
  
  public void clientWrite(byte value) {
    // client writing ...
    client2server.add(value);
  }
  
  public boolean isServer2ClientBufferEmpty() {
    return server2client.isEmpty();
  }
  
  public boolean isClient2ServerBufferEmpty() {
    return client2server.isEmpty();
  }
  
  public int server2ClientBufferSize() {
    return server2client.size();
  }
  
  public int client2ServerBufferSize() {
    return client2server.size();
  }
}
