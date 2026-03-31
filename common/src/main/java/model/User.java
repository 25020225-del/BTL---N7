package model;

public abstract class User extends Entity{
    protected String userName;
    protected String userPass;
    protected String name;
    protected String email;
    protected String role;

    public User() {}


    public User(String id, String userName, String userPass, String name, String email, String role) {
        super(id);
        this.userName = userName;
        this.userPass = userPass;
        this.name = name;
        this.email = email;
        this.role = role;
    }


    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getUserPass() { return userPass; }
    public void setUserPass(String userPass) { this.userPass = userPass; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

}