package simpleServerClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * A simple client sending a message to a server and receiving a message in
 * return
 * 
 * @author Nastaran Shafiei
 */
public class Client {
  public static void main (String[] arguments) throws IOException {
    Socket socket = null;

    final String HOST = java.net.InetAddress.getLocalHost().getHostName();
    final int PORT = 1024;

    try {
      socket = new Socket(HOST, PORT);
      int value;

      OutputStream socketOutput = socket.getOutputStream();
      InputStream socketInput = socket.getInputStream();

      value = 20;
      socketOutput.write(value);
      System.out.println("Client> write " + value);

      value = socketInput.read();
      System.out.println("Client> read " + value);

      socket.close();
    } catch (UnknownHostException e) {
      System.out.println("Host is unknown!");
    } catch (IOException e) {
      System.out.println("I/O error occured when creating the socket!");
    }
  }
}
