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
     * Fires an event to all registered listeners.
     */
    public static void fireEvent(String propertyName, Object newValue) {
        support.firePropertyChange(propertyName, null, newValue);
    }
}
