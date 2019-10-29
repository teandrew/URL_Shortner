import java.net.*;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.sql.*;

public class DatabaseThread extends Thread {
	private Socket socket = null;
	private Connection dbConnection = null;
    
    final String DATABASE = "database.txt";
    final boolean verbose = true;

    public DatabaseThread(Socket socket, Connection conn){
        super("DatabaseThread");
		this.socket = socket;
		this.dbConnection = conn;
    }

    public void run(){
        BufferedReader in = null; PrintWriter out = null;
		
		try {
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream());
			
			String input = in.readLine();
			if(verbose)System.out.println("first line: "+input);

			Pattern ping_pattern = Pattern.compile("^PING$");
			Matcher ping_matcher = ping_pattern .matcher(input);
			Pattern insert_pattern = Pattern.compile("^INSERT (\\S+) (\\S+)$");
            Matcher insert_matcher = insert_pattern .matcher(input);
			if(ping_matcher.matches()){
				if(verbose)System.out.println("Recieved Ping. Responding Pong");
				out.println("PONG");
				out.flush();
			}
            // Insert into database with given key and value
			else if(insert_matcher.matches()){
				String shortResource = insert_matcher.group(1);
                String longResource = insert_matcher.group(2);

                if(verbose)System.out.println("Inserting key: "+ shortResource + ", value: " + longResource);
				insertOrReplace(shortResource, longResource);
				
				out.println("INSERT SUCCESS");
				out.flush(); 
            } 
            else {
				Pattern find_pattern = Pattern.compile("^FIND (\\S+)$");
                Matcher find_matcher = find_pattern.matcher(input);
                // Find value in database with given key
				if(find_matcher.matches()){
					String shortResource = find_matcher.group(1);

                    if(verbose)System.out.println("Searching for key: " + shortResource);
                    String longResource = find(shortResource);
                    // Value found
					if(longResource != null){	
						if(verbose)System.out.println("Found value: " + longResource);					
						out.println("FIND SUCCESS");
						out.println(longResource); 
						out.flush(); 
                    }
                    // Value not found 
                    else {
						if(verbose)System.out.println("Did not find value ");
						out.println("FIND FAIL");
						out.flush(); 
					}
                }
                else{
                    out.println("REQUEST UNKOWN");
                    out.flush(); 
                    if (verbose) {
                        System.out.println("Unrecognized command.\n");
                    }
                }
			}
		} catch (Exception e) {
			System.err.println("Database Server error: " + e.toString());
		} finally {
			try {
				if (in != null) {in.close();}
				if (out != null) {out.close();}
				socket.close(); // we close socket connection
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			
			if (verbose) {
				System.out.println("Connection closed.\n");
			}
		}
    }

    private String find(String shortURL){
		String sql = "SELECT longUrl FROM URLS WHERE shortUrl = ?";
		String longURL = null;

		try (PreparedStatement prepStatement = dbConnection.prepareStatement(sql)){
			prepStatement.setString(1, shortURL);
			try (ResultSet rs = prepStatement.executeQuery()) {
				while (rs.next()) {
					longURL = rs.getString("longUrl");
				}
			} catch (SQLException e) {
				System.out.println("Sql error : " + e);
				return null;
			}
		} catch (SQLException e) {
			System.out.println("Find error : " + e);
			return null;
        }
		return longURL;
	}

	private void insertOrReplace(String shortURL,String longURL){
		String sql = "INSERT OR REPLACE INTO URLS(shortUrl,longUrl) VALUES(?,?)";

		try (PreparedStatement prepStatement = dbConnection.prepareStatement(sql)) {
			prepStatement.setString(1, shortURL);
			prepStatement.setString(2, longURL);
			prepStatement.executeUpdate();
		} catch (SQLException e) {
			System.out.println("Insert error :" + e);
		} 
	}

}