import java.net.*;
import java.io.*;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URLShortnerThread extends Thread {
    private Socket socket = null;

    final File WEB_ROOT = new File(".");
	final String METHOD_NOT_SUPPORTED = "not_supported.html";
	final String INSERT_SUCCESS = "insert_success.html";
	final String INSERT_FAILURE = "insert_failure.html";
	final String FIND_SUCCESS = "find_success.html";
	final String FIND_FAILURE = "find_failure.html";
    final String DATABASE_HOST = "localhost";
    
    final int DATABASE_PORT = 8081;
    final boolean verbose = true;

    public URLShortnerThread(Socket socket){
        super("URLShortnerThread");
        this.socket = socket;
    }

    public void run() {
        BufferedReader in = null; PrintWriter out = null; BufferedOutputStream dataOut = null;
		
		try {
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream());
			dataOut = new BufferedOutputStream(socket.getOutputStream());
			
			String input = in.readLine();
			
			if(verbose)System.out.println("first line: "+input);
			Pattern pput = Pattern.compile("^PUT\\s+/\\?short=(\\S+)&long=(\\S+)\\s+(\\S+)$");
			Matcher mput = pput.matcher(input);
			if(mput.matches()){
				String shortResource=mput.group(1);
				String longResource=mput.group(2);
				String httpVersion=mput.group(3);

				if(save(shortResource, longResource)){
					File file = new File(WEB_ROOT, INSERT_SUCCESS);
					writeResponse(file, out, dataOut, "HTTP/1.1 200 OK");
				}
				else {
					File file = new File(WEB_ROOT, INSERT_FAILURE);
					writeResponse(file, out, dataOut, "HTTP/1.1 500 Internal Server Error");
				}
			} else {
				Pattern pget = Pattern.compile("^(\\S+)\\s+/(\\S+)\\s+(\\S+)$");
				Matcher mget = pget.matcher(input);
				if(mget.matches()){
					String method=mget.group(1);
					String shortResource=mget.group(2);
					String httpVersion=mget.group(3);

					String longResource = find(shortResource);
					if(longResource!=null){
						File file = new File(WEB_ROOT, FIND_SUCCESS);
						writeResponse(file, out, dataOut, "HTTP/1.1 307 Temporary Redirect", longResource);
					} else {
						File file = new File(WEB_ROOT, FIND_FAILURE);
						writeResponse(file, out, dataOut, "HTTP/1.1 404 File Not Found");
					}
				}
				else{
					if (verbose) { System.out.println("Unrecognized request"); }
					File file = new File(WEB_ROOT, METHOD_NOT_SUPPORTED);
					writeResponse(file, out, dataOut, "HTTP/1.1 400 Bad Request");
				}
			}
		} catch (Exception e) {
			System.err.println("Server error");
		} finally {
			try {
				socket.close();
				if (in != null) {in.close();}
				if (out != null) {out.close();}
				if (dataOut != null) {dataOut.close();}
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			
			if (verbose) {
				System.out.println("Connection closed.\n");
			}
		}
    }

    private String find(String shortURL){
		BufferedReader in = null; PrintWriter out = null;
		Socket db_socket = null;
		String longURL = null;
		try {
			db_socket = new Socket(DATABASE_HOST, DATABASE_PORT);
			in = new BufferedReader(new InputStreamReader(db_socket.getInputStream()));
			out = new PrintWriter(db_socket.getOutputStream());

			out.println("FIND " + shortURL);
			out.flush();

			String input = in.readLine();
			if (input.equals("FIND SUCCESS")) {
				if(verbose){System.out.println("FIND SUCCESS: ");}
				longURL = in.readLine();
				if(verbose){System.out.println(longURL);}
				return longURL;
			}
			else {
				if(verbose){System.out.println("FIND FAILURE: ");}
				if(verbose){System.out.println(input);}
				return null;
			}
		} 
		catch (Exception e) {
			System.err.println("Node find error : " + e.toString());
		} finally {
			try {
				if (in != null) {in.close();}
				if (out != null) {out.close();}
				if (db_socket != null) {db_socket.close();}
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			
			if (verbose) {
				System.out.println("DatabaseConnection closed.\n");
			}
		}
		return longURL;
	}

    private boolean save(String shortURL,String longURL){
		BufferedReader in = null; PrintWriter out = null;
		Socket db_socket = null;
		try {
			db_socket = new Socket(DATABASE_HOST, DATABASE_PORT);

			in = new BufferedReader(new InputStreamReader(db_socket.getInputStream()));
			out = new PrintWriter(db_socket.getOutputStream());

			out.println("INSERT " + shortURL + " " + longURL);
			out.flush();

			String input = in.readLine();
			if (input.equals("INSERT SUCCESS")) {
				if(verbose){System.out.println("INSERT SUCCESS");}
				return true;
			}
			else{
				if(verbose){System.out.println("INSERT NO SUCCESS");}
				if(verbose){System.out.println(input);}
				return false;
			}
		} 
		catch (Exception e) {
			System.err.println("Node Insert error : " + e.toString());
		} finally {
			try {
				if (in != null) {in.close();}
				if (out != null) {out.close();}
				if (db_socket != null) {db_socket.close();}
			} catch (Exception e) {
				System.err.println("Error closing stream : " + e.getMessage());
			} 
			
			if (verbose) {
				System.out.println("DatabaseConnection closed.\n");
			}
		}
		return false;
	}
	
	private byte[] readFileData(File file, int fileLength) throws IOException {
		FileInputStream fileIn = null;
		byte[] fileData = new byte[fileLength];
		
		try {
			fileIn = new FileInputStream(file);
			fileIn.read(fileData);
		} finally {
			if (fileIn != null) 
				fileIn.close();
		}
		
		return fileData;
	}

	private void writeResponse(File file, PrintWriter out, BufferedOutputStream dataOut, String firstline) throws IOException{
		int fileLength = (int) file.length();
		String contentMimeType = "text/html";
		//read content to return to client
		byte[] fileData = readFileData(file, fileLength);
			
		out.println(firstline);
		out.println("Server: Java HTTP Server/Shortner : 1.0");
		out.println("Date: " + new Date());
		out.println("Content-type: " + contentMimeType);
		out.println("Content-length: " + fileLength);
		out.println(); 
		out.flush(); 

		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
	}

	private void writeResponse(File file, PrintWriter out, BufferedOutputStream dataOut, String firstline, String longResource) throws IOException{
		int fileLength = (int) file.length();
		String contentMimeType = "text/html";
		//read content to return to client
		byte[] fileData = readFileData(file, fileLength);
			
		out.println(firstline);
		out.println("Location: "+longResource);
		out.println("Server: Java HTTP Server/Shortner : 1.0");
		out.println("Date: " + new Date());
		out.println("Content-type: " + contentMimeType);
		out.println("Content-length: " + fileLength);
		out.println(); 
		out.flush(); 

		dataOut.write(fileData, 0, fileLength);
		dataOut.flush();
	}
}