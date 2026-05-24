package model.base;

/**
 * Abstract base component providing a common structure for structural domain entity identification.
 */
public abstract class Entity {

    private String id;

    public Entity() {
    }

    /**
     * Initializes an entity instance with a unique identifier.
     *
     * @param id the unique character string constraint identification key
     */
    public Entity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Generates a structural text overview block mapping key internal attributes.
     *
     * @return a serialized profile summary string representation
     */
    public abstract String getInfo();
}