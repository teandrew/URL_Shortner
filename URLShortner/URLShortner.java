import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.ConnectException;
import java.net.URL;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Timer;
import java.util.TimerTask;

public class URLShortner { 
	
	static final File WEB_ROOT = new File(".");
	static final String SERVICE_UNAVAILABLE = "service_unavailable.html";
	static final String DATABASE_HOST = "localhost";
	// port to listen connection
	static final int PORT = 8080;
	static final int DATABASE_PORT = 8081;
	
	// verbose mode
	static final boolean verbose = true;

	static final String configFileName = "URLShortner.config";
	private static URLShortnerConfig config;

	private static boolean checkupTimerSet = false;

	public static void main(String[] args) {
		ServerSocket serverConnect = null;

		config = new URLShortnerConfig(configFileName);
		config.readConfigValues();

		// Check if config file is modified and update it if it is
		Timer t = new Timer();
		TimerTask checkIfModifiedTask = new FileWatcher(new File(configFileName)){
		
			@Override
			protected void onChange(File file) {
				config.readConfigValues();
			}
		};
		t.schedule(checkIfModifiedTask, 10000, 10000);

		try {
			serverConnect = new ServerSocket(PORT);
			System.out.println("Server started.\nListening for connections on port : " + PORT + " ...\n");
			
			// we listen until user halts server execution
			while (true) {

				new URLShortnerThread(serverConnect.accept()).start();
				if (verbose) { System.out.println("Connecton opened. (" + new Date() + ")"); }

				// Ping the database and restart it if it does not respond
				if (!checkupTimerSet) {
					checkupTimerSet = true;
					TimerTask checkupTask = new TimerTask(){
						@Override
						public void run() {
							if (!pingDatabase()){
								if(verbose){System.out.println("Database not responding");}
								// relaunch database
							}
							setCheckupTimerSet(false);
						}
					};
					t.schedule(checkupTask, config.GetCheckUpTime());
				}
			}
		} 
		catch (IOException e) {
			System.err.println("Server Connection error : " + e.getMessage());
		}
	}

	private static boolean pingDatabase() {
		try (Socket db_socket = new Socket(DATABASE_HOST, DATABASE_PORT);
			 BufferedReader in = new BufferedReader(new InputStreamReader(db_socket.getInputStream()));
			 PrintWriter out = new PrintWriter(db_socket.getOutputStream())) {
			
			out.println("PING");
			out.flush();

			String input = in.readLine();
			if (input.equals("PONG")) {
				if(verbose){System.out.println("PING SUCCESS");}
				return true;
			}
			else{
				if(verbose){System.out.println("PING UNSUCCESSFUL");}
				if(verbose){System.out.println(input);}
				return false;
			}
		}
		catch (ConnectException e){
			System.err.println("Cannot connect to database : " + e.toString());
			return false;
		}
		catch (Exception e) {
			System.err.println("Ping error : " + e.toString());
		}
		return false;
	}

	private static void setCheckupTimerSet(boolean bool){
		checkupTimerSet = bool;
	}
}
