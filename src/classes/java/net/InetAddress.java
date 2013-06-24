package java.net;

/** 
 * @author Nastaran Shafiei
 */
public class InetAddress {
  String hostName;

  public static InetAddress getLocalHost() throws UnknownHostException {
    InetAddress localhost = new InetAddress();
    localhost.hostName = "localhost";
    return localhost;
  }

  public String getHostName() {
    return hostName;
  }
}
