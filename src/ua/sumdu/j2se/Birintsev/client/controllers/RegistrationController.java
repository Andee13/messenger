package ua.sumdu.j2se.Birintsev.client.controllers;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionRequest;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionResponse;
import ua.sumdu.j2se.Birintsev.common.connection.status.ConnectionRequestStatus;
import ua.sumdu.j2se.Birintsev.common.sendable.Sendable;
import ua.sumdu.j2se.Birintsev.common.utill.IO;

import java.io.IOException;

public class RegistrationController {

    public RegistrationController(MainController mainController){
        this.mainController = mainController;
    }

    private Stage stage;
    private MainController mainController;

    @FXML
    private RadioButton male;

    @FXML
    private RadioButton female;

    @FXML
    private ToggleGroup sex;

    @FXML
    private PasswordField password;

    @FXML
    private PasswordField passwordConfirmation;

    @FXML
    private TextField login;

    @FXML
    private TextField email;

    @FXML
    public void initialize(){

    }

    @FXML
    private TextArea info;

    @FXML
    void sendRegistrationRequest(ActionEvent event) throws IOException {
        ConnectionRequest registrationRequest = new ConnectionRequest(login.getText(), password.getText());
        registrationRequest.setInfo(info.getText());
        registrationRequest.setStatus(ConnectionRequestStatus.REGISTRATION);
        try {
            mainController.getOut().writeObject(registrationRequest);
            ConnectionResponse connectionResponse = (ConnectionResponse) mainController.getIn().readObject();

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace(); // TODO logging exceptions
            throw new IOException(e);
        }
    }
}