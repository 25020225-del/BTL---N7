package model.item;

/**
 * Factory class responsible for creating generalized categories of Items.
 * Implements the Factory Method Design Pattern to promote loose coupling
 * and adhere to the Open/Closed Principle.
 */
public class ItemFactory {

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
    public static Item createItem(String type, String id, String itemName, String description, double startingPrice) {
        // Fallback to a general physical item if the type is not provided
        if (type == null || type.trim().isEmpty()) {
            System.out.println("[Warning]: Item type is null. Defaulting to TangibleItem.");
            return new TangibleItem(id, itemName, description, startingPrice);
        }

        switch (type.toUpperCase()) {
            case TYPE_TANGIBLE:
                return new TangibleItem(id, itemName, description, startingPrice);
            case TYPE_DIGITAL:
                return new DigitalItem(id, itemName, description, startingPrice);
            case TYPE_SERVICE:
                return new ServicePackage(id, itemName, description, startingPrice);
            default:
                System.out.println("[Warning]: Unknown item type '" + type + "'. Defaulting to TangibleItem.");
                return new TangibleItem(id, itemName, description, startingPrice);
        }
    }
}