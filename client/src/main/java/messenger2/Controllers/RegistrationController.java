package messenger2.Controllers;



import java.io.IOException;
import java.io.StringWriter;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import messenger2.App;
import messenger2.ReaderThread;
import messenger2.message.Message;
import messenger2.message.MessageStatus;
import static messenger2.Utils.*;
import javax.xml.bind.JAXBException;

import static messenger2.Utils.getMarshaller;

public class RegistrationController {

    @FXML
    private Label errrorMessange;

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
        errrorMessange.setVisible(false);
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

        confirm.setOnAction(e->{
            try {
                Message message = new Message();
                message.setStatus(MessageStatus.REGISTRATION);
                message.setLogin(loginField.getText());
                message.setPassword(passwordField.getText());
                try {
                    StringWriter stringWriter = new StringWriter();
                    getMarshaller().marshal(message, stringWriter);
                    String str = stringWriter.toString();
                    writer.writeUTF(str);
                } catch (JAXBException ex){
                    System.out.println(ex);
                }



                ReaderThread thread = new ReaderThread();
                thread.start();
                thread.join();
                System.out.println(thread.message);
                if(thread.message != null) {
                    switch (thread.message.getStatus().toString()) {
                        case "ACCEPTED":
                            Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Login.fxml"));
                            App.getStage().setTitle("Hello World");
                            App.getStage().setScene(new Scene(root, 800, 500));
                            App.getStage().show();
                            break;
                        case "DENIED":
                            errrorMessange.setText("Error occur,check your data");
                            errrorMessange.setVisible(true);
                            break;
                        default:
                            errrorMessange.setText("Error occur, problems with connetion");
                            errrorMessange.setVisible(true);
                            break;
                    }
                } else {
                    errrorMessange.setText("Error occur, problems with connetion");
                    errrorMessange.setVisible(true);
                }

//                Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Chat.fxml"));
//                App.getStage().setTitle("Login");
//                App.getStage().setScene(new Scene(root, 800, 500));
//                App.getStage().show();
            } catch (IOException | InterruptedException ex) {
                System.out.println(ex);
            }
        });
    }

}





