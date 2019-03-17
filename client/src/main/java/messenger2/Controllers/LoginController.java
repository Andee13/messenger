package messenger2.Controllers;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import messenger2.App;
import messenger2.ReaderThread;
import messenger2.Utils;
import messenger2.message.Message;
import messenger2.message.MessageStatus;
import javax.xml.bind.JAXBException;
import static messenger2.Utils.*;
import java.io.*;
import java.net.Socket;


public class LoginController {


    @FXML
    private Text ErrorText;

    @FXML
    private JFXButton Register;

    @FXML
    private Text ErrorD;


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
        ErrorD.setVisible(false);
        ErrorText.setVisible(false);

        loginButton.setOnAction(event -> {
            try {
                Message message = new Message();
                message.setStatus(MessageStatus.AUTH);
                message.setLogin(Username.getText());
                message.setPassword(password.getText());
                socket = new Socket("localhost", 5940);
                reader = new DataInputStream(socket.getInputStream());
                writer = new DataOutputStream(socket.getOutputStream());

                try {
                    StringWriter stringWriter = new StringWriter();
                    getMarshaller().marshal(message, stringWriter);
                    String str = stringWriter.toString();
                    writer.writeUTF(str);
                    name = Username.getText();
                    id = name.hashCode();
                    Utils.password = password.getText();
                } catch (JAXBException ex){
                    System.out.println("67"+ex);
                }

                //System.out.println(writer.toString());
                //System.out.println(reader.available());

                ReaderThread thread = new ReaderThread();
                thread.start();
                thread.join();
                System.out.println(thread.message);
                if(thread.message != null) {
                    switch (thread.message.getStatus().toString()) {
                        case "ACCEPTED":
                            Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Chat.fxml"));
                            App.getStage().setTitle("Hello World");
                            App.getStage().setScene(new Scene(root, 800, 500));
                            App.getStage().show();
                            break;
                        case "DENIED":
                            ErrorD.setText(thread.message.getText());
                            break;
                        default:
                            ErrorText.setVisible(true);
                            break;
                    }
                }


            } catch (IOException | InterruptedException ex) {
                System.out.println(ex);
            }
            }
        );
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
