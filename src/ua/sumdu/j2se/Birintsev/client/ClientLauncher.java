package ua.sumdu.j2se.Birintsev.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ua.sumdu.j2se.Birintsev.client.controllers.LoginController;
import ua.sumdu.j2se.Birintsev.client.controllers.MainController;

public class ClientLauncher extends Application {
    public void start(Stage primaryStage) throws Exception{
        MainController mainController = new MainController();
        mainController.showLogin();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
