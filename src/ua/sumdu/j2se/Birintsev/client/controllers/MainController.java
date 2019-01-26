package ua.sumdu.j2se.Birintsev.client.controllers;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ua.sumdu.j2se.Birintsev.client.UserData;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class MainController {
    public MainController(){
        mainStage = new Stage();
        loginController = new LoginController(this);
        registrationController = new RegistrationController(this);
        chatController = new ChatController(this);
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setController(loginController);
            loader.setLocation(LoginController.class.getResource("/ua/sumdu/j2se/Birintsev/client/controllers/fxml/login.fxml"));
            loginScene = new Scene(loader.load());

            loader = new FXMLLoader();
            loader.setController(registrationController);
            loader.setLocation(LoginController.class.getResource("/ua/sumdu/j2se/Birintsev/client/controllers/fxml/registration.fxml"));
            registrationScene = new Scene(loader.load());

            loader = new FXMLLoader();
            loader.setController(chatController);
            loader.setLocation(LoginController.class.getResource("/ua/sumdu/j2se/Birintsev/client/controllers/fxml/main.fxml"));
            chatScene = new Scene(loader.load());
        } catch (IOException e) {
            e.printStackTrace(); // logger
        }
    }

    private UserData userData;
    private Stage mainStage;
    private LoginController loginController;
    private Scene loginScene;
    private RegistrationController registrationController;
    private Scene registrationScene;
    private ChatController chatController;
    private Scene chatScene;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public Stage getMainStage(){
        return mainStage;
    }

    public LoginController getLoginController(){
        return loginController;
    }

    public RegistrationController getRegistrationController(){
        return registrationController;
    }

    public void showLogin(){
        mainStage.setScene(loginScene);
        mainStage.setTitle("Login");
        mainStage.show();
    }

    public void showRegistration(){
        mainStage.setScene(registrationScene);
        mainStage.setTitle("Registration");
        mainStage.show();
    }

    public void showChat(){
        mainStage.setScene(chatScene);
        mainStage.setTitle("Chat");
        mainStage.show();
    }

    private void loadUserData(){

    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    public void setOut(ObjectOutputStream out) {
        this.out = out;
    }

    public void setIn(ObjectInputStream in) {
        this.in = in;
    }

    public Socket getSocket() {
        return socket;
    }

    public ObjectOutputStream getOut() {
        return out;
    }

    public ObjectInputStream getIn() {
        return in;
    }

    public void closeAll() throws IOException {
        in.close();
        out.close();
        socket.close();
    }
}
