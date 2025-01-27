/*-
 * =================================LICENSE_START==================================
 * rapier-example-server
 * ====================================SECTION=====================================
 * Copyright (C) 2024 - 2025 aleph0
 * ====================================SECTION=====================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ==================================LICENSE_END===================================
 */
package rapier.example.server;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A toy server that listens on a port and provides a simple command line interface to list
 * imaginary users.
 */
public class Server implements Runnable {
  public static class ServerThread extends Thread {
    private final Socket clientSocket;
    private final DataStore store;

    public ServerThread(Socket clientSocket, DataStore store) {
      this.clientSocket = clientSocket;
      this.store = store;
    }

    @Override
    public void run() {
      try (InputStream in = clientSocket.getInputStream();
          BufferedReader lines =
              new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
          OutputStream out = clientSocket.getOutputStream();
          PrintWriter writer = new PrintWriter(out, false)) {

        writer.println("Welcome to the user list server!");
        writer.println("Here are the commands you can use:");
        writer.println("LIST - List all user names");
        writer.println("BYE - Quit the server");
        writer.println();
        writer.flush();

        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
          if (line.equals("BYE")) {
            writer.println("Bye!");
            writer.println();
            writer.flush();
            break;
          }

          if (line.equals("LIST")) {
            final List<String> users = store.listUserNames();
            for (String userName : users)
              writer.println(userName);
          } else {
            writer.println("Unknown command: " + line);
          }

          writer.println();
          writer.flush();
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private final int port;
  private final DataStore store;

  public Server(int port, DataStore store) {
    this.port = port;
    this.store = store;
  }

  @Override
  @SuppressWarnings("resource")
  public void run() {
    final ServerSocket serverSocket;
    try {
      serverSocket = new ServerSocket(port);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }
    try (serverSocket) {
      while (true) {
        final Socket clientSocket = serverSocket.accept();
        new ServerThread(clientSocket, store).start();
      }
    } catch (InterruptedIOException e) {
      System.err.println("Server interrupted");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
