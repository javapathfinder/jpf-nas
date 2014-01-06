package java.net;

/**
 * @author Nastaran Shafiei
 */
public class InetSocketAddress extends SocketAddress {
  private String hostname;
  
  // The port number of the Socket Address
  private int port;
  
  public InetSocketAddress(String hostname, int port) throws UnknownHostException {
    checkHost(hostname);
    this.hostname = hostname;
    this.port = port;
  }
  
  public InetSocketAddress(int port) throws UnknownHostException {
    this.hostname = InetAddress.getLocalHost().hostName;
    this.port = port;
  }
  
  private static String checkHost(String hostname) {
    if (hostname == null) {
        throw new IllegalArgumentException("hostname can't be null");
    }
    return hostname;
  }
  
  public final String getHostName() {
    return this.hostname;
  }
  
  public final int getPort() {
    return port;
  }
}
