import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class DatabaseConfig {
    private String configFileName;

    private String databaseURL = "jdbc:sqlite:database.db";
    private String databasePath = "./database.db";
    private String databaseCopyPath = "./copy/";
    private String databaseBackupPath = "./backup/";
    private String backupHostFilePath = null;
    private int backupTime = 10000;

    public DatabaseConfig(String configFileName){
        this.configFileName = configFileName;
    }

    public void readConfigValues(){
		Properties props = new Properties();
		try (FileInputStream in = new FileInputStream(configFileName)){
            props.load(in);
            
            databaseURL = props.getProperty("databaseURL");
            databasePath = props.getProperty("databasePath");
            databaseCopyPath = props.getProperty("databaseCopyPath");
            databaseBackupPath = props.getProperty("databaseBackupPath");
            backupHostFilePath = props.getProperty("backupHostFilePath", null);
            backupTime = Integer.parseInt(props.getProperty("backupTime", "10000"));

		} catch(FileNotFoundException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		} catch(IOException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}
    }
    
    public String GetDatabaseURL(){
        return databaseURL;
    }

    public String GetDatabasePath(){
        return databasePath;
    }

    public String GetDatabaseCopyPath(){
        return databaseCopyPath;
    }

    public String GetDatabaseBackupPath(){
        return databaseBackupPath;
    }

    public String GetBackupHostFilePath(){
        return backupHostFilePath;
    }

    public int GetBackupTime(){
        return backupTime;
    }
}