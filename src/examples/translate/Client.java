package translate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client {
  List<String> languages = new ArrayList<String>();
  String id;

  // number of words to translate
  static int num_translate = 1;
  
  static final String[] phrase = { "Hi", "Bye", "World" };

  public Client (String[] args) {
    for(String arg: args) {
      this.languages.add(arg);
    }
  }
  
  // this application iterates over args until catches a valid lnaguage
  public final static void main (String args[]) {
    new Client(args).start();
  }

  public void start () {
    try {
      Socket socket = new Socket();
      InetSocketAddress addr = new InetSocketAddress("localhost", 4444);
      socket.connect(addr);
      System.out.println("Client is connected.");

      // that makes the server to get block on readLine()
      //enforceDeadlock();
      
      OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream());
      InputStreamReader istr = new InputStreamReader(socket.getInputStream());
      BufferedReader in = new BufferedReader(istr);
      
      // first, agree on a valid language with server
      String language = null;
      for(String l: languages) {
        // let the server know what is the language
        out.write("language::" + l + "\n");
        String response = in.readLine();
        if(response.equals("valid")) {
          language = l;
          id = "[" + language.toUpperCase() + " Client]";
          System.out.println(id + " Client is connected.");
          break;
        }
      }

      // if the language is supported, send your translation request
      if(language!=null) {
        for (int i = 0; i < num_translate; i++) {
          out.write(phrase[i] + "\n");
          out.flush();
          System.out.println(id + " translation request: \"" + phrase[i] + "\" ");

          String translate = in.readLine();
          System.out.println(id + " received:" + translate);
        }
      }

      out.close();
    } catch (IOException e) {
      System.err.println(id + " error" + e);
    }
    
    System.out.println(id + " done!");
  }
  
  public static void enforceDeadlock() {
    final Object o1 = new Object();
    final Object o2 = new Object();
    
    Thread intruder = new Thread (new Runnable() {
      public void run() {
        holdOn(o2,o1);
      }
    });
    intruder.start();
    
    holdOn(o1,o2);
  }
  
  public static void holdOn(Object o1, Object o2) {
    synchronized (o1) {
      synchronized (o2) {
      }
    }
  }
}
