package net.nas;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import nas.util.test.TestNasJPF;

import org.junit.Test;

import gov.nasa.jpf.util.TypeRef;
import gov.nasa.jpf.util.test.TestMultiProcessJPF;

public class SocketTest extends TestNasJPF {
  String[] args = { "+search.multiple_errors = true",
                    "+vm.process_finalizers = true",
                    "+vm.nas.initiating_target = 0"
                  };
  
  int port = 1024;
  final String HOST = "localhost";
  
  @Test
  public void testEstablishingConnection() throws IOException {
    if (mpVerifyNoPropertyViolation(2, args)) {
      
      switch(getProcessId()) {
      case 0:
        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(10);
        Socket sock1 = null;
        try {
          sock1 = serverSocket.accept();
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
  public void testTimedoutAccept() throws IOException {
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
    if (mpVerifyNoPropertyViolation(2, args)) {
     
      switch(getProcessId()) {
      case 0:
        ServerSocket serverSocket = new ServerSocket(port);
        Socket sock1 = serverSocket.accept();
        
        int h1 = getHash(sock1);
        assertTrue(h1!=0);
        
        OutputStream socketOutput = sock1.getOutputStream();
        
        try {
          socketOutput.write(10);
          int h2 = getHash(sock1);
          assertTrue(h1!=h2);
        } catch(SocketException e) {
          // attempting to write on a close connection
        }
        
        break;
      case 1:
        Socket sock2;
        try {
          sock2 = new Socket(HOST, port);
          
          
        } catch(IOException e) {
          // gets here if there was no server accepting the connection request
          System.out.println("never stablished!!");
        }
        break;
      }
    }
  }
  
  @Test
  public void testTimedoutRead() throws IOException {
    if (mpVerifyNoPropertyViolation(2, args)) {
      
      switch(getProcessId()) {
      case 0:
        ServerSocket serverSocket = new ServerSocket(port);
        Socket sock1 = serverSocket.accept();
        assertTrue(sock1.isConnected());
        break;
        
      case 1:
        Socket sock2 = null;
        try {
          sock2 = new Socket(HOST, port);
          sock2.setSoTimeout(10);
          assertTrue(sock2.isConnected());
        } catch(IOException e) {
          // gets here if there was no server accepting the connection request
          return;
        }
        
        try {
          InputStream in = sock2.getInputStream();
          in.read();
          //assertTrue(isOtherEndClosed());
        } catch(SocketTimeoutException e) {
          return;
        } catch(SocketException e) {
          return;
        }
        
        break;
      }
    }
  }
}
