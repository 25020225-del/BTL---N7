package server;
import java.io.*;
import java.net.Socket;
public class ClientHandler implements Runnable{
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String clientName;
    public ClientHandler(Socket socket){
        this.socket=socket;
        try{
            this.in=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.out=new PrintWriter(socket.getOutputStream(),true);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    @Override
    public void run() {
        try {
            out.println("[System]: Please enter your ID:");
            this.clientName=in.readLine();
            if(this.clientName==null) {
                System.err.println("Unregognised ID has disconnected");
                return;
            } else if(!clientName.trim().equalsIgnoreCase("STOP")){
                out.println("[System]: Your ID has been successfully recognised.");
                System.out.println(clientName+" has connected.");
            }
            else{
                System.out.println("Unrecognised ID has ignored registering ID and stopped connecting");
            }
            MultiThreadedServer.broadcast("[System]: "+clientName+" has connected.",this);
            String message;
            while((message=in.readLine())!=null){
                if("STOP".equalsIgnoreCase(message.trim())){
                    System.out.println(clientName+" has stopped connecting.");
                    break;
                }
                System.out.println("[" + clientName + "]: "+message);
                MultiThreadedServer.broadcast("["+clientName+"]: "+message,this);
            }
            if(message==null&&!clientName.trim().equalsIgnoreCase("STOP")){
                System.out.println(clientName+" has stopped connecting.");
            }
        } catch (IOException e) {
            System.out.println("Connection lost with "+clientName);
        } finally {
            closeConnection();
        }
    }

    public void sendMessage(String message){
        out.println(message);
    }

    private void closeConnection() {
        MultiThreadedServer.removeClient(this);
        MultiThreadedServer.broadcast("[System]: " + clientName + " has disconnected.", this);
        try {
            if(socket!=null&&!socket.isClosed())socket.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    public String getClientName() {return this.clientName;}
    public void forceDisconnect(String reason){
        try{
            out.println("[System]: You have been kicked by Admin. Reason: "+reason);
            if (socket!=null&&!socket.isClosed())socket.close();
            System.exit(0);
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}