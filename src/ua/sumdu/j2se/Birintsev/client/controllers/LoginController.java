package ua.sumdu.j2se.Birintsev.client.controllers;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionRequest;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionResponse;
import ua.sumdu.j2se.Birintsev.common.connection.status.ConnectionRequestStatus;

import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class LoginController extends Application {

    public LoginController(MainController mainController){
        this.mainController = mainController;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

    }

    private ConnectionResponse connectionResponse;

    Stage mainStage;
    private MainController mainController;

    @FXML
    private TextField ip;

    @FXML
    private TextField port;

    @FXML
    private TextField login;

    @FXML
    private TextField password;

    @FXML
    private Button loginBtn;

    @FXML
    private Button registrationBtn;

    @FXML
    void sendConnectionRequest(ActionEvent event) {
        try (Socket socket = new Socket(InetAddress.getByName(ip.getText()), Integer.parseInt(port.getText()))) {
            mainController.setSocket(socket);
            mainController.setOut(new ObjectOutputStream(socket.getOutputStream()));
            mainController.setIn(new ObjectInputStream(socket.getInputStream()));
            connectionResponse = connect(login.getText(), password.getText());
            switch(connectionResponse.getStatus()) {
                case ACCEPTED:
                    mainController.showChat();
                    break;
                case ERROR:
                    mainController.getSocket().close();
                    mainController.getIn().close();
                    mainController.getOut().close();
                    System.err.println(connectionResponse.getException().getMessage()); // logger
                    break;
                case CREATED:
                    System.err.println(new StringBuilder(getClass().toString()).append(": Unknown error")); // logger
                    break;
                case REGISTRATION_REQUESTED:
                    Stage stage = new Stage();
                    FXMLLoader loader = new FXMLLoader();
                    ModalDialogController controller = new ModalDialogController(mainStage);
                    controller.getMainLabel().setText("It looks like you have not registrated yet");
                    controller.getMainLabel().setVisible(true);
                    controller.getMainLabel().setText("Would you like to join us?");
                    controller.getSecondaryLabel().setVisible(true);
                    controller.getOkBtn().setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent event) {
                            mainController.showRegistration();
                        }
                    });
                    controller.getCancelBtn().setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent event) {
                            stage.close();
                        }
                    });
                    loader.setController(controller);
                    loader.setLocation(LoginController.class.getResource("/ua/sumdu/j2se/Birintsev/client/controllers/fxml/modalDialog.fxml"));
                    try{
                        Parent root = loader.load();
                        Scene scene = new Scene(root);
                        stage.setTitle("Oops...");
                    } catch (IOException e){
                        e.printStackTrace(); // logger
                    }
                    break;
                case DENIED:

                    break;
            }
        } catch (UnknownHostException e) {
            e.printStackTrace(); // logger
            SystemTray tray = SystemTray.getSystemTray();
            Image image = Toolkit.getDefaultToolkit().createImage("icon.png");
            TrayIcon trayIcon = new TrayIcon(image);
            trayIcon.setImageAutoSize(true);
            try {
                tray.add(trayIcon);
            } catch (AWTException e1) {
                e1.printStackTrace(); // logger
            }
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace(); // logger
        }
    }

    @FXML
    void registration(ActionEvent event) {
        mainController.showRegistration();
    }

    private ConnectionResponse connect (String login, String password) throws IOException, ClassNotFoundException{
        ConnectionRequest connectionRequest = new ConnectionRequest(login, password);
        connectionRequest.setStatus(ConnectionRequestStatus.LOGIN);
        mainController.getOut().writeObject(connectionRequest);
        ConnectionResponse connectionResponse = (ConnectionResponse) mainController.getIn().readObject();
        return connectionResponse;
    }

    public ConnectionResponse getConnectionResponse() {
        return connectionResponse;
    }
}
