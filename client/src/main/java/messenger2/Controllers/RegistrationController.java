package messenger2.Controllers;


import java.io.IOException;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import messenger2.App;

public class RegistrationController {

    @FXML
    private JFXTextField loginField;

    @FXML
    private JFXTextField passwordField;

    @FXML
    private JFXButton confirm;

    @FXML
    private JFXButton alreadyAMember;

    @FXML
    void initialize() {
        alreadyAMember.setOnAction(e->{
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Login.fxml"));
                App.getStage().setTitle("Login");
                App.getStage().setScene(new Scene(root, 800, 500));
                App.getStage().show();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        });
        loginField.setOnAction(e->{

        });
        passwordField.setOnAction(e->{

        });
        confirm.setOnAction(e->{
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Chat.fxml"));
                App.getStage().setTitle("Login");
                App.getStage().setScene(new Scene(root, 800, 500));
                App.getStage().show();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        });
    }

}





