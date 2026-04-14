package client;
import org.example.demo.Launcher;
import client.Client;
public class ClientCallLauncher implements Runnable{
    public void run(){
        try {
            Thread launcher=new Thread();
            launcher.start();
            Launcher.main(null);
            if (!launcher.isAlive()) {
                Client.disableLauncherNotifier();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
