package model;

public class Admin extends User {

    public Admin(String id, String userName, String userPass, String name) {
        super(id, userName, userPass, name, "Admin");
    }
    public Admin(User admin) {
        super(admin.getId(), admin.getUserName(), admin.getUserPass(), admin.getName(), "Admin");
    }


    @Override
    public String getInfo() {
        return    "[Admin] ID: " + this.getId()
                + " | Name: " + this.getName() + " (System Administrator)";
    }

}