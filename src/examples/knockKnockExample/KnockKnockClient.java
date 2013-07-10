/*
 * Copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle or the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package knockKnockExample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class KnockKnockClient {
  private static String[] answers = { "Who's there?", 
                                      "Turnip who?", 
                                      "no" };
  
  public static void main (String[] args) throws IOException {
    Socket kkSocket = null;
    PrintWriter out = null;
    BufferedReader in = null;
    
    String HOST = InetAddress.getLocalHost().getHostName();
    try {
      kkSocket = new Socket(HOST, 4444);
      out = new PrintWriter(kkSocket.getOutputStream(), true);
      System.out.println("creating buffer reader...");
      in = new BufferedReader(new InputStreamReader(kkSocket.getInputStream()));
      System.out.println("created ..." + in);
    } catch (UnknownHostException e) {
      System.err.println("Don't know about host: " + HOST + ".");
      // System.exit(1);
      return;
    } catch (IOException e) {
      System.err.println("Couldn't get I/O for the connection to: " + HOST + ".");
      // System.exit(1);
      return;
    }
    
    BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
    String fromServer;
    String fromUser;
    
    int i=0;
    while ((fromServer = in.readLine()) != null) {
      System.out.println("Server: " + fromServer);
      if (fromServer.equals("Bye.")) break;

      fromUser = answers[i++]; //stdIn.readLine(); - made it play automatically
      if (fromUser != null) {
        System.out.println("Client: " + fromUser);
        out.println(fromUser);
      }
    }
    
    out.close();
    in.close();
    stdIn.close();
    kkSocket.close();
  }
}
