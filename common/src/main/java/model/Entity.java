package model;

public abstract class Entity {
    protected String id;
    protected String mission;

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

    public String getMission() {return mission;}
    public void setMission(String mission) {this.mission = mission;}

    public abstract String getInfo();
}