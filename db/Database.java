import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.net.ServerSocket;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.sql.*;

public class Database { 
	static final String DATABASE = "database.txt";
	// port to listen connection
	static final int PORT = 8081;
	
	// verbose mode
	static final boolean verbose = true;

	static final String configFileName = "Database.config";
	private static DatabaseConfig config;

	private static boolean backupTimerSet = false;

	public static void main(String[] args) {
		Connection conn = null;

		config = new DatabaseConfig(configFileName);
		config.readConfigValues();

		if(true){
			// Run script to move backup to local if exists
			retrieveBackup();
		}

		// Check if config file is modified and update it if it is
		Timer t = new Timer();
		TimerTask checkIfModifiedTask = new FileWatcher(new File(configFileName)){
		
			@Override
			protected void onChange(File file) {
				if(verbose){System.out.println("Config file change detected");}
				config.readConfigValues();
			}
		};
		t.schedule(checkIfModifiedTask, 10000, 10000);

		try {
			conn = openDatabaseConnection();
			createNewTableIfNotExists(conn);

			ServerSocket serverConnect = new ServerSocket(PORT);
			System.out.println("Database Server started.\nListening for connections on port : " + PORT + " ...\n");
			
			// we listen until user halts server execution
			while (true) {
                new DatabaseThread(serverConnect.accept(), conn).start();
				if (verbose) { System.out.println("Connecton opened. (" + new Date() + ")"); }
				
				// Backup the database if it is time
				if (!backupTimerSet) {
					backupTimerSet = true;
					TimerTask backupTask = new TimerTask(){
						@Override
						public void run() {
							// Run shell script to backup then move copy to another server
							if(verbose){System.out.println("Backing up database");}
							backupDatabase();
							setBackupTimerSet(false);
						}
					};
					t.schedule(backupTask, config.GetBackupTime());
				}
			}
        } 
        catch (IOException e) {
			System.err.println("Database Server Connection error : " + e.getMessage());
		}
		finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                System.out.println(ex.getMessage());
            }
        }
	}

	private static Connection openDatabaseConnection() {
		Connection conn = null;
        try {
			conn = DriverManager.getConnection(config.GetDatabaseURL());
		} 
		catch (SQLException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}

		return conn;
	}

	private static void createNewTableIfNotExists(Connection conn) {        
        String sql = "CREATE TABLE IF NOT EXISTS URLS (\n"
                + "    shortUrl text PRIMARY KEY,\n"
                + "    longUrl text NOT NULL\n"
                + ");";
        
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        } catch (SQLException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
        }
	}
	
	private static void backupDatabase() {
		String copy_cmd = String.format("%s %s %s %s",
									"bash",
									"../shellScripts/copy_db",
									config.GetDatabasePath(),
									config.GetDatabaseCopyPath());

		String move_cmd = String.format("%s %s %s %s %s",
										"bash",
										"../shellScripts/move_db",
										config.GetDatabaseCopyPath(),
										getBackupHost(),
										config.GetDatabaseBackupPath());
		try{
			Process p = Runtime.getRuntime().exec(copy_cmd);
			p.waitFor();
			p = Runtime.getRuntime().exec(move_cmd);
			p.waitFor();
		}
		catch(Exception e){
			System.out.println("Backup database error: " + e);
		}
	}

	private static void retrieveBackup() {
		String move_cmd = String.format("%s %s %s %s %s %s",
										"bash",
										"../shellScripts/move_db",
										config.GetDatabaseBackupPath(),
										getBackupHost(),
										config.GetDatabasePath(),
										"-r");
		try{
			Process p = Runtime.getRuntime().exec(move_cmd);
			p.waitFor();
		}
		catch(Exception e){
			System.out.println("Backup database error: " + e);
		}
	}

	private static String getBackupHost(){
		String host = null;
		try (BufferedReader reader = new BufferedReader(new FileReader(config.GetBackupHostFilePath()))){
            host = reader.readLine();
		} catch(Exception e) {
			System.out.println("Error backing up host: " + e);
		}
		return host;
	}

	private static void setBackupTimerSet(boolean bool){
		backupTimerSet = bool;
	}
}
