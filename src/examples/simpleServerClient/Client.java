package simpleServerClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author Nastaran Shafiei
 */
public class Client {
  public static void main (String[] arguments) throws IOException {
    
    // uncomment this to observe local choices in the dot file
    //(new Thread()).start();
    
    final String HOST = java.net.InetAddress.getLocalHost().getHostName();
    final int PORT = Server.PORT;

    Socket socket = new Socket(HOST, PORT);
    //socket.setSoTimeout(2);
    
    OutputStream socketOutput = socket.getOutputStream();
    socketOutput.write("request".getBytes());
    
    socket.close();
  }
}
