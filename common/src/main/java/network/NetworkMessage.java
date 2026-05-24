package network;

/**
 * Standardized data transfer object (DTO) encapsulating network traffic messages.
 * Facilitates uniform serialization and deserialization pathways across remote execution layers.
 */
public class NetworkMessage {

    private String command;
    private Object data;

    public NetworkMessage() {
    }

    /**
     * Constructs a validated network message envelope payload.
     *
     * @param command remote target execution directive route header key
     * @param data    un-serialized contextual transaction element state metadata
     */
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