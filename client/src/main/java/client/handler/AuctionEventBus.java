package client.handler;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * A simple Event Bus using PropertyChangeSupport to decouple UI controllers
 * from network handlers.
 */
public class AuctionEventBus {
    private static final PropertyChangeSupport support = new PropertyChangeSupport(new Object());

    public static final String PRICE_UPDATED = "PRICE_UPDATED";
    public static final String AUCTION_CREATED = "AUCTION_CREATED";
    public static final String DEPOSIT_SUCCESS = "DEPOSIT_SUCCESS";
    public static final String GENERAL_ERROR = "GENERAL_ERROR";
    public static final String GENERAL_SUCCESS = "GENERAL_SUCCESS";

    public static final String BID_SUCCESS = "BID_SUCCESS";
    public static final String FETCH_AUCTIONS_SUCCESS = "FETCH_AUCTIONS_SUCCESS";
    public static final String FETCH_MY_AUCTIONS_SUCCESS = "FETCH_MY_AUCTIONS_SUCCESS";
    public static final String FETCH_TRANSACTIONS_SUCCESS = "FETCH_TRANSACTIONS_SUCCESS";
    public static final String FETCH_USERS_SUCCESS = "FETCH_USERS_SUCCESS";
    public static final String ADMIN_ACTION_SUCCESS = "ADMIN_ACTION_SUCCESS";
    public static final String FETCH_WALLET_SUCCESS = "FETCH_WALLET_SUCCESS";

    /**
     * Subscribes a listener to a specific event.
     */
    public static void addListener(String propertyName, PropertyChangeListener listener) {
        support.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Unsubscribes a listener.
     */
    public static void removeListener(String propertyName, PropertyChangeListener listener) {
        support.removePropertyChangeListener(propertyName, listener);
    }

    /**
     * Unsubscribes all the listener with the same name.
     */
    public static void removeAllListeners(String propertyName) {
        for (PropertyChangeListener l : support.getPropertyChangeListeners(propertyName)) {
            support.removePropertyChangeListener(propertyName, l);
        }
    }

    /**
     * Fires an event to all registered listeners.
     */
    public static void fireEvent(String propertyName, Object newValue) {
        support.firePropertyChange(propertyName, null, newValue);
    }
}
