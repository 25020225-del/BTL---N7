package network;

/**
 * Represents a standardized data packet for communication between the client and the server.
 * This class encapsulates an action command and an optional data payload,
 * making it easy to serialize and deserialize messages into JSON format via libraries like Jackson.
 */
public class NetworkMessage {

    private String command;
    private Object data;

    /**
     * Default constructor.
     * Required by serialization libraries (e.g., Jackson) for JSON deserialization.
     */
    public NetworkMessage() {}

    /**
     * Constructs a new NetworkMessage with a specific command and data payload.
     *
     * @param command The action command identifier (e.g., "LOGIN", "CREATE_AUCTION", "UPDATE_AUCTION_PRICE").
     * @param data    The associated data payload, which can be any serializable object or primitive type.
     */
    public NetworkMessage(String command, Object data) {
        this.command = command;
        this.data    = data;
    }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

}