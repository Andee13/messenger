package ua.sumdu.j2se.Birintsev.client.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import static ua.sumdu.j2se.Birintsev.common.utill.Utill.hideNodes;

public class ModalDialogController {

    public ModalDialogController(Stage owner){
        this.owner = owner;
        hideNodes(mainLabel, secondaryLabel, responseField);
    }

    public Stage getOwner() {
        return owner;
    }

    public AnchorPane getModalDialog() {
        return modalDialog;
    }

    public Label getMainLabel() {
        return mainLabel;
    }

    public Label getSecondaryLabel() {
        return secondaryLabel;
    }

    public TextField getResponseField() {
        return responseField;
    }

    public Button getOkBtn() {
        return okBtn;
    }

    public Button getCancelBtn() {
        return cancelBtn;
    }

    private Stage owner;

    @FXML
    private AnchorPane modalDialog;

    @FXML
    private Label mainLabel;

    @FXML
    private Label secondaryLabel;

    @FXML
    private TextField responseField;

    @FXML
    private Button okBtn;

    @FXML
    private Button cancelBtn;

}
