package model;

public class Admin extends User {

    public Admin(String id, String userName, String userPass, String name) {
        super(id, userName, userPass, name, "Admin");
    }

    @Override
    public String getInfo() {
        return    "[Admin] ID: " + this.id
                + " | Name: " + this.name + " (System Administrator)";
    }

}