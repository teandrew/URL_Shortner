import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoadBalancer implements Runnable {
  static final int remoteport = 8080; // Outgoing connections
  static final int localport = 8080; // Incoming connections
  static final String HOSTS = "../shellScripts/hosts"; // Name of file that contains list of hosts
  static SortedMap<Long, String> hashToHostname = new TreeMap<Long, String>(); // Sorted map of hashes (stored as longs) of hostnames

  public static void main(String[] args) throws IOException {
    ServerSocket serverSocket;
    try {
      serverSocket = new ServerSocket(localport);
      System.out.println("Started proxy server on port " + localport);
    } catch (IOException e) {
      throw new RuntimeException("Couldn't open server on port " + localport, e);
    }
    
    System.out.println("Hashing hosts");
    // Fill sorted map of hosts
    hashHosts();

    // Main listening loop; never terminates
    while (true) {
      try {
        // Spawn a new thread for each incoming connection
        new Thread(new LoadBalancer(serverSocket.accept())).start();
        System.out.println("Accepted new connection");
      } catch (IOException e) {
        System.err.println("Client connection error : " + e.getMessage());
        throw new RuntimeException("Error accepting client connection", e);
      }
    }
  }

  // Fills the 'hashToHostname' map
  public static void hashHosts() throws IOException{
    try {
      FileReader fileReader = new FileReader(new File(HOSTS));
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line = bufferedReader.readLine(); // Ignore the first host in the list (since it refers the loadbalancer itself)
      while ((line = bufferedReader.readLine()) != null) {
        hashToHostname.put(stringToHashedLong(line), line);
      }
      bufferedReader.close();
      fileReader.close();
    } catch (IOException e) {
      System.err.println("Hash host error : " + e.getMessage());
    }
  }

  // Returns a long representation of the hash of 'input'
  public static long stringToHashedLong(String input){
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(input.getBytes());
      byte[] digest = md.digest();
      return((new BigInteger(digest)).longValue());
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    return 0;
  }
  
  // Takes the short from the request and finds out which host (hostname) we should map this request to
  public static String mapToHost(String shortRequest){
    long hashOfShort = stringToHashedLong(shortRequest);
    SortedMap<Long, String> rest = hashToHostname.tailMap(hashOfShort);
    if (rest.isEmpty()) { // The hash of the short is mapped in front of the last node on the circle, so...
      return hashToHostname.get(hashToHostname.firstKey()); // ... it gets mapped to the first node in the entire map (since the short is "behind" the first node)
    }
    else { // The hash of the short is mapped to the next node on the circle (first node in the tailmap we generated)
      return rest.get(rest.firstKey());
    }
  }

  // Extract the 'short' from the HTTP request
  public static String getShort(String line){
    Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$"); // PUT /?short=____&long=____ 
    Matcher mput = pput.matcher(line);
    if (mput.matches()) {
      return mput.group(1);
    }
    else {
      Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");
      Matcher mget = pget.matcher(line);
      if (mget.matches()) {
        return mget.group(2);
      }
    }
    return "";
  }


  private Socket incoming;
  public LoadBalancer(Socket incoming) {
    this.incoming = incoming;
  }

  public void run(){
    try {
      this.talkToServer(this.incoming);
    } catch (IOException e) {
      System.err.println("Error handling client connection :" + e.getMessage());
    }
  }
  /**
   * Handles a single client-to-API-server transaction
   */
  public void talkToServer(Socket client) throws IOException {

    final byte[] request = new byte[1024];
    byte[] reply = new byte[4096];

    // This will hold the connection to one of the API servers
    Socket server = null;

    try {
      final InputStream streamFromClient = client.getInputStream();
      final OutputStream streamToClient = client.getOutputStream();

      // Find out which host to connect to based on "short"
      BufferedReader in = new BufferedReader(new InputStreamReader(streamFromClient));
      String firstLine = in.readLine();
      System.out.println("Read first line: " + firstLine);
      System.out.println("Mapping to host");
      String serverName = mapToHost(getShort(firstLine));

      // Make a connection to the real server.
      // If we cannot connect to the server, send an error to the
      // client and disconnect
      try {
        server = new Socket(serverName, remoteport);
        System.out.println("Connected to server");
      } catch (IOException e) {
        System.out.println("Server connection error: " + e);
        PrintWriter out = new PrintWriter(streamToClient);
        out.print("Proxy server cannot connect to " + serverName + ":" + remoteport + ":\n" + e + "\n");
        out.flush();
        client.close();
      }

      // Get server streams.
      final InputStream streamFromServer = server.getInputStream();
      final OutputStream streamToServer = server.getOutputStream();

      // a thread to read the client's requests and pass them
      // to the server. A separate thread for asynchronous.
      Thread t = new Thread() {
        public void run() {
          int bytesRead;
          try {
            PrintWriter out = new PrintWriter(streamToServer);
            out.println(firstLine);
            out.flush();
            while ((bytesRead = streamFromClient.read(request)) != -1) {
              streamToServer.write(request, 0, bytesRead);
              streamToServer.flush();
            }
          } catch (IOException e) {
            System.out.println("Client write error: " + e);
          }

          // the client closed the connection to us, so close our
          // connection to the server.
          try {
            streamToServer.close();
          } catch (IOException e) {
            System.out.println("Server write Stream close error: " + e);
          }
        }
      };

      // Start the client-to-server request thread running
      System.out.println("Starting thread to write from client to server");
      t.start();

      // Read the server's responses
      // and pass them back to the client.
      int bytesRead;
      System.out.println("Writing response to client from server");
      try {
        while ((bytesRead = streamFromServer.read(reply)) != -1) {
          streamToClient.write(reply, 0, bytesRead);
          streamToClient.flush();
        }
      } catch (IOException e) {
        System.out.println("Server response error: " + e);
      }

      // The server closed its connection to us, so we close our
      // connection to our client.
      streamToClient.close();
    } catch (IOException e) {
      System.err.println(e);
    } finally {
      try {
        if (server != null)
          server.close();
        if (client != null)
          client.close();
      } catch (IOException e) {
        System.out.println("Close error: " + e);
      }
    }

  }
}
