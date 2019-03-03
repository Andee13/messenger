package messenger2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import static messenger2.Utils.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;


public class App extends Application {

    public static Stage stage;

    public static Stage getStage() {
        return stage;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        stage = primaryStage;

        socket = new Socket("localhost", 5940);
        reader = new DataInputStream(socket.getInputStream());
        writer = new DataOutputStream(socket.getOutputStream());

        Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Login.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.show();
    }



    public static void main(String[] args) {
        launch(args);
    }
}