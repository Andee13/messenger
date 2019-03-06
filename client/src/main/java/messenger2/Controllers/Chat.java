package messenger2.Controllers;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import javax.imageio.ImageIO;

public class Chat {

    @FXML
    private BorderPane borderPane;

    @FXML
    private ListView<?> chatPane;

    @FXML
    private TextArea messageBox;

    @FXML
    private Button buttonSend;

    @FXML
    private HBox onlineUsersHbox;

    @FXML
    private ListView<String> userList;

    @FXML
    private ImageView humburger;

    @FXML
    private ImageView userImageView;

    @FXML
    private Label usernameLabel;

    @FXML
    void initialize() {
        String name = "HEllo";

        userList.setItems(FXCollections.observableArrayList("heg", "dgd"));
//            userList.setCellFactory(listView -> new ListCell<String>() {
//                private ImageView imageView = new ImageView();
//
//
//                @Override
//                public void updateItem(String friend, boolean empty) {
//                    super.updateItem(friend, empty);
//                    if (empty) {
//                        setText(null);
//                        setGraphic(null);
//                    } else {
//                        //try {
//                        Image image = new Image("/messenger2/res/boy1.png");
//                        System.out.println(image);
//                        imageView.setImage(image);
//                        setText("friend");
//                        setGraphic(imageView);
////                        } catch (IOException ex){
////                            System.out.println(ex);
////                        }
//                    }
//                }
//            });

    }
}
