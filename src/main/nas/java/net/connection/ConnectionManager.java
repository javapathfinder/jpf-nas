package nas.java.net.connection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.ListenerAdapter;
import gov.nasa.jpf.util.StateExtensionClient;
import gov.nasa.jpf.util.StateExtensionListener;
import gov.nasa.jpf.vm.ElementInfo;
import gov.nasa.jpf.vm.MJIEnv;
import gov.nasa.jpf.vm.ThreadInfo;
import gov.nasa.jpf.vm.VM;
import nas.java.net.connection.Connection;

/**
 * It captures the current states of all connections made along this execution paths
 * 
 * @author Nastaran Shafiei
 */
public class ConnectionManager implements StateExtensionClient<List<Connection>> {

  // the list of all connections established along "this" execution path
  public List<Connection> curr;

  ConnectionManager() {
    this.curr = new ArrayList<Connection>();
  }
  
  /*------ connections management ------*/
  
  static ConnectionManager connections;
  
  /**
   * This is invoked by NasVM.initSubsystems()
   */
  public static void init (Config config) {
    connections = new ConnectionManager();
    connections.registerListener(VM.getVM().getJPF());
  }

  public static ConnectionManager getConnections() {
    return connections;
  }
  
  public Connection getPendingServerConn(int port, String serverHost) {
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
  
  public Connection getPendingClientConn(int port, String serverHost) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.hasClient() && conn.isPending()) {
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
        if(conn.getClientEndSocket()==endpoint || conn.getServerEndSocket()==endpoint) {
          return conn;
        }
      }
    }
 
    return null;
  }
  
  public void addNewPendingServerConn(int serverPassiveSocket, int port, String serverHost) {
    VM vm = VM.getVM();
    Connection conn = new Connection(port);
    // the server connection is pending, that is, we don't have serverEndSocket yet and 
    // for now is set to null
    conn.setServerInfo(serverPassiveSocket, MJIEnv.NULL, vm.getApplicationContext(serverPassiveSocket));
    this.curr.add(conn);
  }

  public void addNewPendingClientConn(int client, int port, String serverHost) {
    VM vm = VM.getVM();
    Connection conn = new Connection(port);
    conn.setClientInfo(client, vm.getApplicationContext(client), serverHost);
    this.curr.add(conn);
  }

  public void closeConnection(int endpoint) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.isConnectionEndpoint(endpoint)) {
        conn.close();
        return;
      }
    }
    // there was not a connection with the given endpoint
    throw new ConnectionException("Could not find the connection to close");
  }
  
  public void terminateConnection(int endpoint) {
    Iterator<Connection> itr = curr.iterator();
    while(itr.hasNext()) {
      Connection conn = itr.next();

      if(conn.isConnectionEndpoint(endpoint) || conn.getServerPassiveSocket()==endpoint) {
        conn.terminate();
        return;
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
  
  //return a deep copy of the connections - a new clone is needed every time
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
  
  /**
   * This listener updates connections list upon socket objects getting garbage 
   * collected.
   */
  public class ConnectionTerminationListener extends ListenerAdapter {

    @Override
    public void objectReleased(VM vm, ThreadInfo currentThread, ElementInfo releasedObject) {
      if(releasedObject.instanceOf("Ljava.net.Socket;") || releasedObject.instanceOf("Ljava.net.ServerSocket;")) {
        //int objRef = releasedObject.getObjectRef();
        //terminateConnection(objRef);        
      }
    }
  }
  
  /*------ state extension management ------*/
  
  @Override
  public List<Connection> getStateExtension () {
    return cloneConnections(this.curr);
  }

  @Override
  public void restore (List<Connection> stateExtension) {
    curr = cloneConnections(stateExtension);
  }

  @Override
  public void registerListener (JPF jpf) {
    StateExtensionListener<List<Connection>> sel = new StateExtensionListener<List<Connection>>(this);
    jpf.addListener(sel);
    
    ConnectionTerminationListener ctl = new ConnectionTerminationListener();
    jpf.addListener(ctl);
  }
}
