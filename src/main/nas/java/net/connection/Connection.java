package nas.java.net.connection;

import java.util.Iterator;
import  gov.nasa.jpf.util.OATHash;

import gov.nasa.jpf.util.ArrayByteQueue;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.MJIEnv;

public class Connection  implements Cloneable {

  MJIEnv env;
  
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
  
  public Connection(MJIEnv env, int port) {
    this.env = env;
    
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
    } else {
      this.updateHash();
    }
  }
  
  protected void setClientInfo(int client, ApplicationContext clientApp, String serverHost) {
    this.clientEndSocket = client;
    this.clientApp = clientApp;
    this.serverHost = serverHost;

    if(this.hasPassiveServer()) {
      this.establish();
    } else {
      this.updateHash();
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
    if(this.hasPassiveServer()) {
      this.setServerEndSocket(serverEndSocket);
      this.setClientInfo(clientEndSocket, clientApp, host);
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

  public boolean hasPassiveServer() {
    return (this.serverPassiveSocket!=MJIEnv.NULL);
  }

  public boolean hasActiveServer() {
    return (this.serverEndSocket!=MJIEnv.NULL);
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
    this.updateHash();
  }

  public boolean isEstablished() {
    return(this.state==State.ESTABLISHED);
  }

  protected void close() {
    this.state = State.CLOSED;
    this.updateHash();
  }

  public boolean isClosed() {
    return(this.state==State.CLOSED);
  }
  
  protected void terminate() {
    this.state = State.TERMINATED;
    this.updateHash();
  }

  public boolean isTerminated() {
    return(this.state==State.TERMINATED);
  }
  
  public void setPending() {
    this.state = State.PENDING;
    this.updateHash();
  }
  
  public boolean isPending() {
    return(this.state==State.PENDING);
  }
  
  public String toString() {
    String result = "\nserverPassiveSocket: " + this.serverPassiveSocket + " serverEnd:" + this.serverEndSocket +" (host:" + this.serverHost +")" + " <---port:" + 
      this.port + "--->" + " clientEnd:" + this.clientEndSocket + " ["+this.state+"]\n";
    result += "clinet>=>server buffer: " + client2server + "\n";
    result += "server>=>client buffer: " + server2client + "\n";
    return result;
  }
  
  public byte serverRead() {
    // server reading ...
    byte val = client2server.poll().byteValue();
    this.updateHash();
    return val;
  }
  
  public byte clientRead() {
    // client reading ...
    byte val = server2client.poll().byteValue();
    this.updateHash();
    return val;
  }
  
  public void serverWrite(byte value) {
    // server writing ...
    server2client.add(value);
    this.updateHash();
  }
  
  public void clientWrite(byte value) {
    // client writing ...
    client2server.add(value);
    this.updateHash();
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
  
  public int hashCode() {
    int h = 0;
    
    // include server-2-client buffer
    Iterator<Byte> itr = server2client.iterator();
    while(itr.hasNext()) {
      h = OATHash.hashMixin(h, itr.next());
    }
    
    // include client-2-server buffer
    itr = client2server.iterator();
    while(itr.hasNext()) {
      h = OATHash.hashMixin(h, itr.next());
    }
    
    // include the state of the connection
    h = OATHash.hashMixin(h, state.ordinal());
    
    return OATHash.hashFinalize(h);
  }
  
  public void updateHash() {
    int h = hashCode();
    
    // TODO - we might get rid of the second part of the if conditions once we handle
    // processes shutdown semantics
    if(this.clientEndSocket!=MJIEnv.NULL && env.getElementInfo(clientEndSocket)!=null) {
      env.getModifiableElementInfo(this.clientEndSocket).setIntField("hash", h);
    }
    
    if(this.serverEndSocket!=MJIEnv.NULL && env.getElementInfo(serverEndSocket)!=null) {
      env.getModifiableElementInfo(this.serverEndSocket).setIntField("hash", h);
    }
  }
}
