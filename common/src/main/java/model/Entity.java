package model;

public abstract class Entity {
    protected String id;
    public Entity() {
    }

    public Entity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public abstract String getInfo();
}