package model.base;

/**
 * Abstract base class for all domain entities within the system.
 * It provides a common structure for uniquely identifying objects using a string-based ID,
 * and mandates a standardized way to retrieve entity information.
 */
public abstract class Entity {

    private String id;

    /**
     * Default constructor.
     */
    public Entity() {
    }

    /**
     * Constructs an Entity with a specified unique identifier.
     *
     * @param id The unique ID to be assigned to this entity.
     */
    public Entity(String id) {
        this.id = id;
    }

    /**
     * Retrieves the unique identifier of this entity.
     *
     * @return The entity's ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Sets or updates the unique identifier for this entity.
     *
     * @param id The new unique ID.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Generates a formatted string containing the core details of the entity.
     * Subclasses must implement this method to provide specific information
     * relevant to their context.
     *
     * @return A summary string of the entity's details.
     */
    public abstract String getInfo();
}