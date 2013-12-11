package net.nas;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import nas.util.test.TestNasJPF;

import org.junit.Test;

import gov.nasa.jpf.util.TypeRef;
import gov.nasa.jpf.util.test.TestMultiProcessJPF;

public class SocketTest extends TestNasJPF {
  String[] args = { "+search.multiple_errors=true",
                    "+listener+=,gov.nasa.jpf.listener.DistributedSimpleDot"
                  };
  
  int port = 1024;
  final String HOST = "localhost";
  
  @Test
  public void testEstablishingConnection() throws IOException {
    if (mpVerifyPropertyViolation(2, new TypeRef("gov.nasa.jpf.vm.NotDeadlockedProperty"), args)) {
      
      switch(getProcessId()) {
      case 0:
        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(10);
        Socket sock1 = null;
        try {
          sock1 = serverSocket.accept();
          assertTrue(sock1.isConnected());
        } catch(SocketTimeoutException e) {
          // gets here if no request is coming after a certain amount of time
          assertNull(sock1);
        }
        break;
        
      case 1:
        Socket sock2;
        try {
          sock2 = new Socket(HOST, port);
          assertTrue(sock2.isConnected());
        } catch(IOException e) {
          // gets here if there was no server accepting the connection request
        }
        break;
      }
    }
  }
  
  /**
   * Blocking accept with timeout throws SocketTimeoutException
   */
  @Test
  public void testTimeoutAccept() throws IOException {
    if (mpVerifyUnhandledException(1, "java.net.SocketTimeoutException", args)) {

      ServerSocket serverSocket = new ServerSocket(port);
      serverSocket.setSoTimeout(10);
      serverSocket.accept();
    }
  }
  
  private int getHash(Socket sock) throws Exception {
    Field f = sock.getClass().getDeclaredField("hash");
    f.setAccessible(true);
    return f.getInt(sock);
  }
  
  @Test
  public void testHash() throws Exception {
    if (mpVerifyPropertyViolation(2, new TypeRef("gov.nasa.jpf.vm.NotDeadlockedProperty"), args)) {
     
      switch(getProcessId()) {
      case 0:
        ServerSocket serverSocket = new ServerSocket(port);
        Socket sock1 = serverSocket.accept();
        
        int h1 = getHash(sock1);
        assertTrue(h1!=0);
        
        OutputStream socketOutput = sock1.getOutputStream();
        socketOutput.write(10);
        
        int h2 = getHash(sock1);
        assertTrue(h1!=h2);
        
        break;
      case 1:
        Socket sock2;
        try {
          sock2 = new Socket(HOST, port);
          
          
        } catch(IOException e) {
          // gets here if there was no server accepting the connection request
        }
        break;
      }
    }
  }
}
