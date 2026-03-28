package nhom7.btl.model;

public class User {
    private int id;
    private String userName;
    private String userPass;
    private String name;
    private String email;
    private String role;

    public User() {}


    public User(int id, String userName, String userPass, String name, String email, String role, long balance) {
        this.id = id;
        this.userName = userName;
        this.userPass = userPass;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

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

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(this.role);
    }
}