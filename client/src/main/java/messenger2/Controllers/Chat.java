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
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;

import javax.imageio.ImageIO;

public class Chat {

    @FXML
    private BorderPane borderPane;

    @FXML
    private ListView<String> chatPane;

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
        //Set image
        Image avatar = new Image("messenger2/res/user.png",50,50,false,false);
        userList.setItems(FXCollections.observableArrayList( "salmon", "gold"));
        userList.setCellFactory(param ->  new ListCell<String>() {
            ImageView img = new ImageView();
            public void  updateItem(String name, boolean empty){
                super.updateItem(name, empty);
                if(name != null) {
                    setText(name);
                    img.setImage(avatar);
                    setGraphic(img);
                }else {
                    setGraphic(null);
                }
            }
        });

        String[] messanges = new String[]{"hello", "hi"};
        BackgroundImage myBI= new BackgroundImage(new Image("messenger2/res/blogpic.png",32,32,false,true),
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER,
                BackgroundSize.DEFAULT);
        chatPane.setItems(FXCollections.observableArrayList(messanges));
        chatPane.setCellFactory(p ->  new ListCell<String>() {
            ImageView img = new ImageView();
            public void  updateItem(String name, boolean empty){
                super.updateItem(name, empty);
                if(name != null) {
                    setText(name);
                    //positionInArea(name,0,0,0,0,0,0,0);
                    img.setImage(avatar);
                    setGraphic(img);
                    setBackground(new Background(myBI));

                }else {
                    setGraphic(null);
                }
            }
        });


    }
}
