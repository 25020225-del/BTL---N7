package gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.User;

import java.io.IOException;

public class ClientRegister {
    protected static void onRegisterViewButtonClick() throws IOException {
        TextField registerName = (TextField) MainApplication.rootRegister.lookup("#registerName");
        TextField registerAccountName = (TextField) MainApplication.rootRegister.lookup("#registerAccountName");
        PasswordField registerPasswordAccount = (PasswordField) MainApplication.rootRegister.lookup("#registerPasswordAccount");
        PasswordField registerPasswordAccountChecker = (PasswordField) MainApplication.rootRegister.lookup("#registerPasswordAccountChecker");
        ComboBox<String> registerRole = (ComboBox<String>) MainApplication.rootRegister.lookup("#registerRole");
        registerName.setText("");
        registerAccountName.setText("");
        registerPasswordAccount.setText("");
        registerPasswordAccountChecker.setText("");
        registerRole.setValue(null);
        MainApplication.setNewScene(MainApplication.rootRegister);
    }
    protected static void onRegisterButtonClick() throws IOException {
        TextField registerName = (TextField) MainApplication.rootRegister.lookup("#registerName");
        TextField registerAccountName = (TextField) MainApplication.rootRegister.lookup("#registerAccountName");
        PasswordField registerPasswordAccount = (PasswordField) MainApplication.rootRegister.lookup("#registerPasswordAccount");
        PasswordField registerPasswordAccountChecker = (PasswordField) MainApplication.rootRegister.lookup("#registerPasswordAccountChecker");
        ComboBox<String> registerRole = (ComboBox<String>) MainApplication.rootRegister.lookup("#registerRole");
        if(registerName.getText().trim().isEmpty()){
            AlertHelper.showErrorAlert("Register Failder","Name not entered");
            return;
        }
        if(registerAccountName.getText().trim().isEmpty()){
            AlertHelper.showErrorAlert("Register Failed","Account name not entered");
            return;
        }
        if(registerPasswordAccount.getText().trim().isEmpty()){
            AlertHelper.showErrorAlert("Register Failed","Password not entered");
            return;
        }
        if(!registerPasswordAccount.getText().equals(registerPasswordAccountChecker.getText())) {
            AlertHelper.showErrorAlert("Register Failed","Password Mismatch");
            return;
        }
        if(registerRole.getValue() == null) {
            AlertHelper.showErrorAlert("Register Failed","Role was not chosen");
            return;
        }

        User user = new User("sexgay", registerAccountName.getText(), registerPasswordAccount.getText(), registerName.getText(), registerRole.getValue());
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(user);
        System.out.println(json);

    }
}
