package messenger2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import static messenger2.Utils.*;

public class App extends Application {

    public static Stage stage;

    public static Stage getStage() {
        return stage;
    }

    public static String[] getServerConfigurations() {
        String[] configs = new String[2];
        try {
            File file = new File("client\\target\\classes\\messenger2\\config\\config.xml");
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
            configs[0] = doc.getElementsByTagName("host").item(0).getTextContent();
            configs[1] = doc.getElementsByTagName("host").item(0).getTextContent();
        } catch (ParserConfigurationException | IOException | SAXException ex){
            System.out.println(ex);
        }

        return configs;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        stage = primaryStage;
        //String[] conf = getServerConfigurations();
//        socket = new Socket("localhost", 5940);
//        reader = new DataInputStream(socket.getInputStream());
//        writer = new DataOutputStream(socket.getOutputStream());

        //Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Login.fxml"));

        Parent root = FXMLLoader.load(getClass().getResource("/messenger2/views/Registration.fxml"));
        primaryStage.setTitle("Hello World");
        primaryStage.setScene(new Scene(root, 800, 500));
        String css = getClass().getResource("/messenger2/res/style.css").toString();
        System.out.println(css);
        primaryStage.getScene().getStylesheets().add(css);
        primaryStage.show();
    }



    public static void main(String[] args) {
        launch(args);
    }
}