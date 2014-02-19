package translate;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Translator {

  static boolean simulate;

  static String[] frenchTranslations = { "Salut", "Au revoir", "Monde" };
  static String[] spanishTranslations = { "Hola", "Bye", "Mundo" };
  
  public static final int SERVER_PORT = 4444;

  public static void main (String[] args) throws IOException {
    int num_clients = Integer.parseInt(args[0]);
    ServerSocket receiver = new ServerSocket(SERVER_PORT);
    
    simulate = Boolean.parseBoolean(args[1]);

    System.out.println("[Translator] starts");

    for (int i = 0; i < num_clients; i++) {
      Socket socket;
      try {
        socket = receiver.accept();
        System.out.println("[Translator Server] accepted a connection");

        System.out.println("[Translator Server] Creating Worker...");
        new Worker(socket).start();
      }
      catch(IOException ioe) {
        // do nothing - just move on to serving the next client
      }
    }
  }
}
