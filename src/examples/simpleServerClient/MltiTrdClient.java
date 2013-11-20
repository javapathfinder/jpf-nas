package simpleServerClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/**
 * @author Nastaran Shafiei
 */
public class MltiTrdClient {
  
  public static class Helper extends Thread {
    public void run() {
    }
  }
  
  public static void main (String[] arguments) throws IOException {
    Helper helper = new Helper();
    helper.start();

    final String HOST = java.net.InetAddress.getLocalHost().getHostName();
    final int PORT = Server.PORT;

    Socket socket = new Socket(HOST, PORT);
    socket.setSoTimeout(2);
    
    OutputStream socketOutput = socket.getOutputStream();
    socketOutput.write("request".getBytes());
    
    //socket.close();
  }
}
