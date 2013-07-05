package nas.java.net.connection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import gov.nasa.jpf.JPF;
import gov.nasa.jpf.util.ArrayByteQueue;
import gov.nasa.jpf.util.StateExtensionClient;
import gov.nasa.jpf.vm.ApplicationContext;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.VM;

import nas.java.net.connection.Connections.Connection;

/**
 * It captures the current states of all connections made along this execution paths
 * 
 * @author Nastaran Shafiei
 */
public class Connections implements StateExtensionClient<List<Connection>> {

  // the list of all connections established along "this" execution path
  public List<Connection> curr;

  Connections() {
    this.curr = new ArrayList<Connection>();
  }

  public static class Connection implements Cloneable {
    
    public enum State {
      PENDING,
      ESTABLISHED,
      CLOSED
    };

    String serverHost;
    State state;
    int port;
    
    int server;
    ApplicationContext serverApp;
    
    int client;
    ApplicationContext clientApp;

    // communication buffers
    ArrayByteQueue server2client; // server out and client in
    ArrayByteQueue client2server; // client out and server in
    
    public Connection(int port) {
      this.port = port;
      this.state = State.PENDING;
      
      this.server = MJIEnv.NULL;
      this.client = MJIEnv.NULL;
      
      server2client = new ArrayByteQueue();
      client2server = new ArrayByteQueue();
    }
    
    public int getPort() {
      return this.port;
    }

    public int getServer() {
      return this.server;
    }

    public int getClient() {
      return this.client;
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

    private void setServerInfo(int server, ApplicationContext serverApp) {
      this.server = server;
      this.serverApp = serverApp;
      this.serverHost = serverApp.getHost();

      if(this.hasClient()) {
        this.establish();
      }
    }
    
    private void setClientInfo(int client, ApplicationContext clientApp, String serverHost) {
      this.client = client;
      this.clientApp = clientApp;
      this.serverHost = serverHost;

      if(this.hasServer()) {
        this.establish();
      }
    }
    
    public void establishedConnWithServer(int server, ApplicationContext serverApp) {
      if(this.hasClient()) {
        this.setServerInfo(server, serverApp);
      } else {
        throw new ConnectionException();
      }
    }
    
    public void establishedConnWithClient(int client, ApplicationContext clientApp, String host) {
      if(this.hasServer()) {
        this.setClientInfo(client, clientApp, host);
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
      return (this.server!=MJIEnv.NULL);
    }

    public boolean hasClient() {
      return (this.client!=MJIEnv.NULL);
    }

    // check if the given socket object is an end-point of this connection
    public boolean isConnectionEndpoint(int socket) {
      return (this.client==socket || this.server==socket);
    }

    private void establish() {
      this.state = State.ESTABLISHED;
    }

    public boolean isEstablished() {
      return(this.state==State.ESTABLISHED);
    }

    private void close() {
      this.state = State.CLOSED;
    }

    public boolean isClosed() {
      return(this.state==State.CLOSED);
    }
    
    public boolean isPending() {
      if((this.state==State.PENDING)!=!(this.hasServer() && this.hasClient())) {
        throw new ConnectionException("PENDING status does not math!");
      }
      
      return(!(this.hasServer() && this.hasClient()));
    }
    
    public String toString() {
      String result = "\nserver:" + this.server +" (host:" + this.serverHost +")" + " <---port:" + 
        this.port + "--->" + " client:" + this.client + " ["+this.state+"]\n";
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
  }
  
  
  /*------ connections management ------*/
  
  static Connections connections;

  static {
    connections = new Connections();
    connections.registerListener(VM.getVM().getJPF());
  }

  public static Connections getConnections() {
    return connections;
  }
  
  public Connection getServerPendingConn(int port, String serverHost) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.hasServer() && conn.isPending()) {
        if(conn.getPort()==port && conn.getServerHost().equals(serverHost)) {
          return conn;
        }
      }
    }
 
    return null;
  }
  
  public Connection getServerConn(int port, String serverHost) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.hasServer()) {
        if(conn.getPort()==port && conn.getServerHost().equals(serverHost)) {
          return conn;
        }
      }
    }
 
    return null;
  }
  
  public boolean hasServerConn(int port, String serverHost) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.hasServer()) {
        if(conn.getPort()==port && conn.getServerHost().equals(serverHost)) {
          return true;
        }
      }
    }
 
    return false;
  }
  
  public Connection getClientConn(int port, String serverHost) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.hasClient()) {
        if(conn.getPort()==port && conn.getServerHost().equals(serverHost)) {
          return conn;
        }
      }
    }
 
    return null;
  }

  public Connection getConnection(int endpoint) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.hasClient()) {
        if(conn.getClient()==endpoint || conn.getServer()==endpoint) {
          return conn;
        }
      }
    }
 
    return null;
  }
  
  public void addNewPendingServerConn(int server, int port, String serverHost) {
    VM vm = VM.getVM();
    Connection conn = new Connection(port);
    conn.setServerInfo(server, vm.getApplicationContext(server));
    this.curr.add(conn);
  }

  public void addNewPendingClientConn(int client, int port, String serverHost) {
    VM vm = VM.getVM();
    Connection conn = new Connection(port);
    conn.setClientInfo(client, vm.getApplicationContext(client), serverHost);
    this.curr.add(conn);
  }

  public void closeConnections(int endpoint) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.isConnectionEndpoint(endpoint)) {
        conn.close();
      }
    }
  }
  
  // check if there exists a server with the given host and port 
  // TODO: maybe the server itself is in the list
  public boolean isAddressInUse(String host, int port) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();
      if(conn.getServerHost().equals("host") && conn.getPort()==port) {
        return true;
      }
    }
    return false;
  } 
  
  
  /*------ state extension management ------*/
  
  @Override
  public List<Connection> getStateExtension () {
    return cloneConnections(this.curr);
    //return curr;
  }

  @Override
  public void restore (List<Connection> stateExtension) {
    curr = cloneConnections(stateExtension);
  }

  @Override
  public void registerListener (JPF jpf) {
    DistributedStateExtensionListener<List<Connection>> sel = new DistributedStateExtensionListener<List<Connection>>(this);
    jpf.addSearchListener(sel);
  }
  
  // return a deep copy of the connections - a new clone is needed every time
  // JPF advances or backtracks
  public List<Connection> cloneConnections(List<Connection> list) {
    List<Connection> cloneList = new ArrayList<Connection>();
    Iterator<Connection> itr = list.iterator();
    
    while(itr.hasNext()) {
      Connection clone = (Connection)itr.next().clone();
      cloneList.add(clone);
    }
    
    return cloneList;
  }
}
