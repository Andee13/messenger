package messenger2.Controllers;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import messenger2.App;

import java.io.IOException;

public class LoginController {


    @FXML
    private JFXButton Register;

    @FXML
    private JFXTextField Username;

    @FXML
    private JFXTextField password;

    @FXML
    private JFXButton loginButton;

    @FXML
    private ImageView BackToSystem;

    @FXML
    void initialize() {
        loginButton.setOnAction(event -> {
            String b = Username.getText();
            String a = password.getText();
            System.out.println(b + " " + a);
        });
        Register.setOnAction(event -> {
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Registration.fxml"));
                App.getStage().setTitle("Hello World");
                App.getStage().setScene(new Scene(root, 800, 500));
                App.getStage().show();
            } catch (IOException ex) {
                System.out.println(ex);
            }
        });
        BackToSystem.setOnMouseClicked(e ->{
            System.exit(0);
        });
    }
}
