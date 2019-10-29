import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class URLShortnerConfig {
    private String configFileName;

    private int checkUpTime = 10;

    public URLShortnerConfig(String configFileName){
        this.configFileName = configFileName;
    }

    public void readConfigValues(){
		Properties props = new Properties();
		try (FileInputStream in = new FileInputStream(configFileName)){
            props.load(in);
            
            checkUpTime = Integer.parseInt(props.getProperty("checkUpTime", "10000"));

		} catch(FileNotFoundException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		} catch(IOException e) {
			System.out.println(e.getMessage());
			System.exit(-1);
		}
    }

    public int GetCheckUpTime(){
        return checkUpTime;
    }
}