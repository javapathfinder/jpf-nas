package nas.java.net.connection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import gov.nasa.jpf.JPF;
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
public class Connections implements StateExtensionClient<List<Connection>>{

  // the list of all connections established along "this" execution path
  public List<Connection> curr;

  Connections() {
    this.curr = new ArrayList<Connection>();
  }

  static class Connection implements Cloneable {
    
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

    public Connection(int port) {
      this.port = port;
      this.state = State.PENDING;
      this.server = MJIEnv.NULL;
      this.client = MJIEnv.NULL;
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

    public Connection cloneFor() {
      Connection conn = null;

      try {
        conn = (Connection)this.clone();
      } catch (CloneNotSupportedException e) {
        e.printStackTrace();
      }

      return conn;
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

    public String toString() {
      String result = "\nserver:" + this.server +" (host:" + this.serverHost +")" + " <---port:" + 
        this.port + "--->" + " client:" + this.client + " ["+this.state+"]";
      return result;
    }
 
    public boolean isPending() {
      return(!(this.hasServer() && this.hasClient()));
    }
  }
  
  
  /*------ connections management ------*/
  
  static Connections connections;

  static {
    connections = new Connections();
    connections.registerListener(VM.getVM().getJPF());
  }

  static Connections getConnections() {
    return connections;
  }

  public void setServerInfoFor(Connection conn, int server, ApplicationContext serverApp) {
    List<Connection> list = new ArrayList<Connection>();
    Iterator<Connection> itr = curr.iterator();

    while(itr.hasNext()) {
      Connection c = itr.next();
      Connection clone = c.cloneFor();
      if(c==conn) {
        clone.setServerInfo(server, serverApp);
      }
      list.add(clone);
    }
    this.curr = list;
  }

  public void setClientInfoFor(Connection conn, int client, ApplicationContext clientApp, String host) {
    List<Connection> list = new ArrayList<Connection>();
    Iterator<Connection> itr = curr.iterator();

    while(itr.hasNext()) {
      Connection c = itr.next();
      Connection clone = c.cloneFor();
      if(c==conn) {
        clone.setClientInfo(client, clientApp, host);
      }
      list.add(clone);
    }
    this.curr = list;
  }

  public Connection getServerConn(int port, String serverHost) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();
      // make sure the server is still pending and has not established a connection yet
      if(conn.hasServer()) {
        if(conn.getPort()==port && conn.getServerHost().equals(serverHost)) {
          return conn;
        }
      }
    }
 
    return null;
  }

  public Connection getClientConn(int port, String serverHost) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();
      // make sure the server is still pending and has not established a connection yet
      if(conn.hasClient()) {
        if(conn.getPort()==port && conn.getServerHost().equals(serverHost)) {
          return conn;
        }
      }
    }
 
    return null;
  }

  public void addNewPendingServerConn(int server, int port, String serverHost) {
    VM vm = VM.getVM();
    curr = this.connectionsClone();
    Connection conn = new Connection(port);
    conn.setServerInfo(server, vm.getApplicationContext(server));
    this.curr.add(conn);
  }

  public void addNewPendingClientConn(int client, int port, String serverHost) {
    VM vm = VM.getVM();
    curr = this.connectionsClone();
    Connection conn = new Connection(port);
    conn.setClientInfo(client, vm.getApplicationContext(client), serverHost);
    this.curr.add(conn);
  }

  public void closeConnections(int server) {
    curr = this.connectionsClone();
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();
      // make sure the server is still pending and has not established a connection yet
      if(conn.isConnectionEndpoint(server)) {
        conn.close();
      }
    }
  }
  
  // check if there exists a server with the given host and port 
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

  public List<Connection> connectionsClone() {
    List<Connection> list = new ArrayList<Connection>();
    Iterator<Connection> itr = curr.iterator();

    while(itr.hasNext()) {
      Connection clone = (Connection)itr.next().cloneFor();
      list.add(clone);
    }

    return list;
  }

  
  /*------ state extension management ------*/
  
  @Override
  public List<Connection> getStateExtension () {
    return curr;
  }

  @Override
  public void restore (List<Connection> stateExtension) {
    curr = stateExtension;
  }

  @Override
  public void registerListener (JPF jpf) {
    DistributedStateExtensionListener<List<Connection>> sel = new DistributedStateExtensionListener<List<Connection>>(this);
    jpf.addSearchListener(sel);
  }
}
