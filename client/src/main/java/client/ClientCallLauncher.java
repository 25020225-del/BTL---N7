package client;
import org.example.demo.Launcher;
public class ClientCallLauncher implements Runnable{
    public void run(){
        try {
            Thread launcher=new Thread();
            launcher.start();
            Launcher.main(null);
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
