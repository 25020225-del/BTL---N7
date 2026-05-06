package model.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class responsible for creating generalized categories of Items.
 * Implements the Factory Method Design Pattern to promote loose coupling
 * and adhere to the Open/Closed Principle.
 */
public class ItemFactory {
    private static Logger log = LoggerFactory.getLogger(ItemFactory.class);

    /**
     * Constants representing the broad item categories.
     */
    public static final String TYPE_TANGIBLE = "TANGIBLE";
    public static final String TYPE_DIGITAL = "DIGITAL";
    public static final String TYPE_SERVICE = "SERVICE";

    /**
     * Creates an Item instance based on the specified generalized type.
     *
     * @param type          The broad category of the item (e.g., "TANGIBLE").
     * @param id            The unique identifier for the new item.
     * @param itemName      The name of the item.
     * @param description   A brief description of the item.
     * @param startingPrice The initial bidding price.
     * @return A specific subclass of Item corresponding to the requested type.
     */
    public static Item createItem(String type, String id, String itemName, String description, long startingPrice) {
        // Fallback to a general physical item if the type is not provided
        if (type == null || type.trim().isEmpty()) {
            log.warn("Item type is null. Defaulting to TangibleItem.");
            return new TangibleItem(id, itemName, description, startingPrice);
        }

        return switch (type.toUpperCase()) {
            case TYPE_TANGIBLE -> new TangibleItem(id, itemName, description, startingPrice);
            case TYPE_DIGITAL -> new DigitalItem(id, itemName, description, startingPrice);
            case TYPE_SERVICE -> new ServicePackage(id, itemName, description, startingPrice);
            default -> {
                log.warn("Unknown item type '{}'. Defaulting to TangibleItem.", type);
                yield new TangibleItem(id, itemName, description, startingPrice);
            }
        };
    }
}