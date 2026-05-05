module org.example.demo {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires io.github.cdimascio.dotenv.java;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires transitive common;
    requires jdk.compiler;
    requires java.sql;
    requires org.kordamp.ikonli.javafx;
    requires com.google.zxing;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.security.sasl;
    requires org.java_websocket;
    requires jcommander;
    requires org.slf4j;

    opens gui to javafx.fxml;
    exports gui;
    exports gui.widget;
    opens gui.widget to javafx.fxml;
    exports gui.process;
    opens gui.process to javafx.fxml;
}
