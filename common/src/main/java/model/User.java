package model;

public class User extends Entity {
    protected String userName;
    protected String userPass;
    protected String name;
    protected String role;
    private boolean isGood;

    public User() {
        super();
    }

    // Hàm tạo dùng lúc đăng ký
    public User(String id, String userName, String userPass, String name, String role) {
        super(id);
        this.userName = userName;
        this.userPass = userPass;
        this.name = name;
        this.role = role;
    }

    public User(String id, String userName, String userPass, String name) {
        super(id);
        this.userName = userName;
        this.userPass = userPass;
        this.name = name;
    }

    // --- GETTER & SETTER ---
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserPass() { return userPass; }
    public void setUserPass(String userPass) { this.userPass = userPass; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isGood() { return isGood; }
    public void setGood(boolean good) { isGood = good; }

    @Override
    public String getInfo() {
        return "ID: " + this.id + " | Username: " + this.userName + " | Tên: " + this.name + " | Vai trò: " + this.role;
    }
}