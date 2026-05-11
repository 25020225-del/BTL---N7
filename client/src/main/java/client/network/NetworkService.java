package client.network;

import gui.ClientUserController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkService {

    // The global network client session used across the entire application
    private static NetworkClient instance;
    private static final Logger log = LoggerFactory.getLogger(ClientUserController.class);


    public static NetworkClient get() {
        return instance;
    }

    public static void set(NetworkClient client) {
        instance = client;
    }

    public static void sendMessage(String message, Object object) {
        if(instance!= null) {instance.sendMessage(message, object);}
        else{
            log.error("Client is null");
        }
    }
}