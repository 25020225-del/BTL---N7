module org.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires io.github.cdimascio.dotenv.java;


    opens org.example.demo to javafx.fxml;
    exports org.example.demo;
}