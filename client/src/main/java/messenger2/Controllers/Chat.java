package messenger2.Controllers;

import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXToggleNode;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import messenger2.Waiter;
import messenger2.message.Message;
import messenger2.message.MessageStatus;

import static messenger2.Utils.*;
import javax.imageio.ImageIO;
import javax.naming.Binding;

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
    private JFXToggleNode add;



    @FXML
    private ImageView unnecessaryButton;



    @FXML
    void initialize() {
        buttonSend.setOnAction(e -> {
            sendMessenge(new Message(MessageStatus.ROOM_LIST).setFromId(id));//.setLogin(name).setPassword(password));
        });
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

        Image image  = new Image("messenger2/res/more.png", 20, 20, false, false);
        ImageView imageView = new ImageView(image);
//        imageView.setImage(image);
        imageView.setFitWidth(20);
        imageView.setFitWidth(20);
        if(imageView == null) {
            System.out.println("gdsg");
        }
//        unnecessaryButton.setImage(image);
//        add.setGraphic(imageView);




        //imageView.imageProperty().bind(Bindings.when(add.selectedProperty()).then(avatar).otherwise(avatar));


        String[] messanges = new String[]{"Jack:\nhello Reference site about Lorem Ipsum, giving information \non its origins, as well as a random Lipsum generator.", "Elly:\nhi"};
        BackgroundImage myBI= new BackgroundImage(new Image("messenger2/res/Rectangle.png",300,60,false,true),
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.DEFAULT,
                new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, false, false, true,false));
        chatPane.setItems(FXCollections.observableArrayList(messanges));
        chatPane.setCellFactory(p ->  new ListCell<String>() {
            ImageView img = new ImageView();
            public void  updateItem(String name, boolean empty){
                super.updateItem(name, empty);
                if(name != null) {
                    setText(name);
                   // img.setImage(avatar);
                    setGraphic(img);
                    //setBackground(new Background(myBI));
                    setStyle("-fx-padding: 10px");


                }else {
                    setGraphic(null);
                }
            }
        });


        JFXButton button = new JFXButton("Popup!");
        StackPane main = new StackPane();
        main.getChildren().add(button);

        JFXPopup popup = new JFXPopup(userList);
//        popup.setPopupContent(list);
        button.setOnAction(e -> popup.show(button, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT));
        sendMessenge(new Message(MessageStatus.ROOM_LIST).setFromId(32));

        Waiter waiter = new Waiter();
        waiter.setDaemon(true);
        waiter.start();

        //chatPane.setBackground(new Background());


    }
}
