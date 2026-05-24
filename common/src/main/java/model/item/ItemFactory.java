package model.item;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory coordinator leveraging polymorphic instantiation to decouple
 * product categories from core messaging infrastructure controllers.
 */
public class ItemFactory {

    private static final Logger log = LoggerFactory.getLogger(ItemFactory.class);

    public static final String TYPE_TANGIBLE = "TANGIBLE";
    public static final String TYPE_DIGITAL = "DIGITAL";
    public static final String TYPE_SERVICE = "SERVICE";

    /**
     * Factory method instantiating specialized subclasses of {@link Item} matched by unique string route switches.
     *
     * @param type          the broad architectural category route tracking string
     * @param id            assigned distinct asset key constraint
     * @param itemName      catalog reference metadata title
     * @param description   contextual documentation features summary
     * @param startingPrice base open valuation bounds
     * @return concrete bounded item subtype model package
     */
    public static Item createItem(String type, String id, String itemName, String description, long startingPrice) {
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