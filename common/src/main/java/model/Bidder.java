package model;

public class Bidder extends User {

    public Bidder() { super(); }

    // Tiến hóa từ User thành Bidder
    public Bidder(User baseUser) {
        super(baseUser.getId(), baseUser.getUserName(), baseUser.getUserPass(), baseUser.getName(), "BIDDER");

        // Dù Bidder không xài tới, nhưng copy sang cho đồng bộ với User gốc cũng không thừa
        this.setGood(baseUser.isGood());
    }
}