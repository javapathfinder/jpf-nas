package nas.java.net.connection;

/**
 * This exception is thrown when handling a Connection object in Connections
 * class goes wrong
 * 
 * @author Nastaran Shafiei
 */
@SuppressWarnings("serial")
public class ConnectionException extends RuntimeException {
  
  public ConnectionException () {
    super();
  }

  public ConnectionException (String s) {
    super(s);
  }
}