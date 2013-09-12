package net.nas;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.junit.Test;

import gov.nasa.jpf.util.test.TestMultiProcessJPF;

public class SocketTest extends TestMultiProcessJPF {
  String[] args = { "+search.multiple_errors=true" };

  //To make sure that each process has its own main thread 
  @Test
  public void mainThreadsIdTest() throws IOException {
    if(!isJPFRun()) {
      //
    }

    if (mpVerifyNoPropertyViolation(2)) {
      int prcId = getProcessId();
      int port = 1024;
      final String HOST = java.net.InetAddress.getLocalHost().getHostName();
      
      switch(prcId) {
      
      case 0:
        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.accept();
        System.out.println("aaa");
      case 1:
        new Socket(HOST, port);
        System.out.println("bbb");
      }
    }

    if(!isJPFRun()) {
      //
    }
 }

}
