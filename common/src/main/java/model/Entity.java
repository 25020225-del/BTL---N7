package model;

public abstract class Entity {
    protected String id;
    protected String command;

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

    public String getCommand() {return command;}
    public void setCommand(String command) {this.command = command;}

    public abstract String getInfo();
}