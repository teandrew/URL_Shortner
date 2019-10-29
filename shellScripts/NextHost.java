import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.*;
import java.util.*;

public class NextHost {
  static final String HOSTS = "hosts"; // Name of file that contains list of hosts
  static SortedMap<Long, String> hashToHostname = new TreeMap<Long, String>(); // Sorted map of hashes (stored as longs) of hostnames

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
        System.out.println("Usage: java NextHost LOCALHOSTNAME");
        System.exit(0);
    }
    hashHosts();
    System.out.println(mapToHost(args[0]));
  }

  // Fills the 'hashToHostname' map
  public static void hashHosts() throws IOException{
    try {
      FileReader fileReader = new FileReader(new File(HOSTS));
      BufferedReader bufferedReader = new BufferedReader(fileReader);
      String line = bufferedReader.readLine(); // Ignore the first host in the list (since it refers the loadbalancer)
      while ((line = bufferedReader.readLine()) != null) {
        hashToHostname.put(stringToHashedLong(line), line);
      }
      bufferedReader.close();
      fileReader.close();
    } catch (IOException e) {

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
  
  // Takes the local hostname from the commandline args and finds out which host (hostname) is in front of it
  public static String mapToHost(String local){
    long hashOfLocal = stringToHashedLong(local);
    hashToHostname.remove(hashOfLocal); // Prevents local name from mapping to itself
    SortedMap<Long, String> rest = hashToHostname.tailMap(hashOfLocal);
    if (rest.isEmpty()) { // The hash of the short is mapped in front of the last node on the circle, so...
      return hashToHostname.get(hashToHostname.firstKey()); // ... it gets mapped to the first node in the entire map (since the short is "behind" the first node)
    }
    else { // The hash of the short is mapped to the next node on the circle (first node in the tailmap we generated)
      return rest.get(rest.firstKey());
    }
  }
}