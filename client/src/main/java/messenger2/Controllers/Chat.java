package messenger2.Controllers;


import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXToggleNode;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;


public class Chat {


    @FXML
    private JFXListView<Label> messegersList;

//    private JFXPopup popUp;

    @FXML
    private JFXListView<Label> JFXListView;

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
    void initialize() {/*
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
*/
    //try {
        /*File file = new File("/messenger2/res/style.css");
        System.out.println(file.getAbsolutePath());
        String css = getClass().getResource("/messenger2/res/style.css").toString();
        System.out.println(css);
        JFXListView.getScene().getStylesheets().add(css);
*/
        for (int i = 0; i < 20; i++) {
            Label label = new Label("User " + i);
            label.setPadding(new Insets(10));
            ImageView image = new ImageView(new Image("messenger2/res/user.png"));
            image.setFitHeight(20);
            image.setFitWidth(20);
            label.setGraphic(image);
            JFXListView.getItems().add(label);
        }
        JFXListView.setExpanded(true);
        JFXListView.depthProperty().set(1);
   /* } catch(IOException ex) {
        System.out.println(ex);
        System.out.println(new File("").getAbsolutePath());

    }*/

//        JFXButton button = new JFXButton("Popup!");
//        StackPane main = new StackPane();
//        main.getChildren().add(button);



        //JFXPopup popup = new JFXPopup(userList);
//        popup.setPopupContent(list);
        //button.setOnAction(e -> popup.show(button, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT));
//        sendMessenge(new Message(MessageStatus.ROOM_LIST).setFromId(32));




//        Waiter waiter = new Waiter();
//        waiter.setDaemon(true);
//        waiter.start();



        //chatPane.setBackground(new Background());

        JFXListView.getSelectionModel().select(0);
        renderMessages();
    }
    void renderMessages() {
        for(int i = 0; i < 5; i++) {
            Label label = new Label("message " + i);
            label.setPadding(new Insets(10));
            label.setAlignment(Pos.CENTER_RIGHT);
            messegersList.getItems().add(label);
        }
        messegersList.setCellFactory(list -> new CenteredListViewCell());
    }

    final class CenteredListViewCell extends ListCell<Label> {
        @Override
        protected void updateItem(Label label1, boolean empty) {
            super.updateItem(label1, empty);
            if (empty) {
                setGraphic(null);
            } else {
                // Create the HBox
                HBox hBox = new HBox();
                hBox.setAlignment(Pos.CENTER_RIGHT);
                VBox vBox = new VBox();
                // Create centered Label
                Label label = new Label("fsaf\n");
                label.setAlignment(Pos.CENTER_RIGHT);


                //label.setPadding(new Insets(1));
                vBox.getChildren().add(label);
                vBox.getChildren().add(new Label("hi"));
                System.out.println(vBox.getChildren().get(1));
                hBox.getChildren().add(vBox);
                setGraphic(hBox);
            }
        }
    }

    @FXML
    private void showPopup(MouseEvent event){
        if(event.getButton() == MouseButton.SECONDARY) {
            JFXPopup popUp = new JFXPopup();
            JFXButton jfxButton1 = new JFXButton("Start conversation");
            jfxButton1.setOnAction(e -> {
                System.out.println("Button clicked");
                popUp.hide();
            });
            JFXButton jfxButton2 = new JFXButton("Cancel");
            jfxButton2.setMinWidth(jfxButton1.getPrefWidth());
            jfxButton1.setPadding(new Insets(10));
            jfxButton2.setPadding(new Insets(10));

            VBox vBox = new VBox(jfxButton1, jfxButton2);
            popUp.setPopupContent(vBox);
            popUp.show(JFXListView, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
        }
        //this.popUp.show(JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT );

    }

}
