module org.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires io.github.cdimascio.dotenv.java;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires common;
    requires jdk.compiler;
    requires java.sql;


    opens org.example.demo to javafx.fxml;
    exports org.example.demo;
}