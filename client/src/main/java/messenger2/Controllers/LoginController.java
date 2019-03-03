package messenger2.Controllers;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import messenger2.App;
import messenger2.message.Message;
import messenger2.message.MessageStatus;

import javax.xml.bind.JAXBException;

import static messenger2.Utils.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

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
            try {
                Message message = new Message();
                message.setStatus(MessageStatus.REGISTRATION);
                message.setLogin(Username.getText());
                message.setPassword(password.getText());
                socket = new Socket(InetAddress.getLocalHost(), 5940);
                reader = new Scanner(socket.getInputStream());
                try {
                    getMarshaller().marshal(message, writer);
                } catch (JAXBException ex){
                    System.out.println(ex);
                }
                System.out.println(writer.toString());
                String responseString  = reader.next();

                Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Chat.fxml"));
                App.getStage().setTitle("Hello World");
                App.getStage().setScene(new Scene(root, 800, 500));
                App.getStage().show();
            } catch (IOException ex) {
                System.out.println(ex);
            }
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
