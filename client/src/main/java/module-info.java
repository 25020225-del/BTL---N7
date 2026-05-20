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
    requires net.coobird.thumbnailator;
    requires org.apache.commons.text;
    requires java.string.similarity;

    opens gui to javafx.fxml;
    exports gui;
    exports gui.widget;
    opens gui.widget to javafx.fxml;
    exports gui.process;
    opens gui.process to javafx.fxml;
    exports gui.userController;
    opens gui.userController to javafx.fxml;
    opens gui.userController.table to javafx.fxml;
    exports gui.userController.table;
    opens gui.widget.item to javafx.fxml;
    exports gui.widget.item;
    exports client.network;
    exports client.service;
}
