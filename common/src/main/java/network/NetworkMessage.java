package network;

public class NetworkMessage {
    private String command; // Tên lệnh: "REGISTER", "LOGIN", "PLACE_BID"...
    private Object data;
    public NetworkMessage() {
    }


    public NetworkMessage(String command, Object data) {
        this.command = command;
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}