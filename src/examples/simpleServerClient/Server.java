package simpleServerClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Nastaran Shafiei
 */
public class Server {
  public static final int PORT = 1024;
  
  public static void main (String[] arguments) throws IOException {
    ServerSocket serverSocket = new ServerSocket(PORT);
    //serverSocket.setSoTimeout(2);
    Socket socket = serverSocket.accept();
    
    InputStream socketInput = socket.getInputStream();
    socketInput.read();
    
    //socket.close();
    //serverSocket.close();
  }
}
