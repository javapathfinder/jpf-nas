package serverClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MultiClientServer {
  public static void main (String[] arguments) throws IOException {
    try {
      final int PORT = 1024;
      int value;
      int num_clients = 2;

      ServerSocket serverSocket = new ServerSocket(PORT);
      
      for(int i=0; i<num_clients; i++) {
        Socket socket = serverSocket.accept();
        assert(socket.isConnected());

        OutputStream socketOutput = socket.getOutputStream();
        InputStream socketInput = socket.getInputStream();

        value = socketInput.read();
        System.out.println("Server> read " + value);

        value = 40;
        socketOutput.write(value);
        System.out.println("Server> write " + value);

        socket.close();
      }
      serverSocket.close();
    } catch (IOException e) {
      System.out.println("I/O error occured when opening the socket");
    }
  }
}
