package simpleServerClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A simple server receiving a message from a client and sending a message in
 * return it
 * 
 * @author Nastaran Shafiei
 */
public class Server {
  public static void main (String[] arguments) throws IOException {
    try {
      final int PORT = 1024;
      int value;

      ServerSocket serverSocket = new ServerSocket(PORT);
      Socket socket = serverSocket.accept();

      OutputStream socketOutput = socket.getOutputStream();
      InputStream socketInput = socket.getInputStream();

      value = socketInput.read();
      System.out.println("Server> read " + value);

      value = 40;
      socketOutput.write(value);
      System.out.println("Server> write " + value);

      socket.close();
      serverSocket.close();
    } catch (IOException e) {
      System.out.println("I/O error occured when opening the socket");
    }
  }
} 