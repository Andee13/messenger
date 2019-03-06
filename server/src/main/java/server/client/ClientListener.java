package server.client;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import server.Server;
import server.ServerProcessing;
import server.exceptions.ClientNotFoundException;
import server.exceptions.IllegalPasswordException;
import server.exceptions.RoomNotFoundException;
import server.room.Room;
import server.room.RoomProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.xpath.*;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import static common.Utils.buildMessage;

/**
 *  The class {@code ClientListener} handles operating incoming connections i.e. it's methods
 * interact with requests from client side
 *
 *  NOTE! The methods are called in the method {@code handle(Message message)} do not throw any exceptions.
 * The point that their purpose is to execute an operation to be requested from client's side and return
 * a pieces of information about an executed (or not) operation. Thus in case if requested actions have not
 * been performed properly the methods return instances of {@code Message} of statuses {@code MessageStatus.ERROR}
 * or {@code MessageStatus.DENIED}. Some additional information may be provided in the field {@code Message.text}
 * */
public class ClientListener extends Thread {

    private volatile Socket socket;
    private volatile Server server;
    private volatile DataOutputStream out;
    private volatile DataInputStream in;
    private boolean logged;
    private Client client;

    public Client getClient() {
        return client;
    }

    private static final Logger LOGGER = Logger.getLogger("ClientListener");

    public ClientListener(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void run() {
        if (server == null) {
            LOGGER.fatal("Server must not be null");
            interrupt();
            return;
        } else if (!State.RUNNABLE.equals(server.getState())) {
            LOGGER.fatal(buildMessage("Server must have", State.RUNNABLE
                    , "state, but currently it's state is", server.getState()));
            interrupt();
            return;
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            String messageXml;
            socket.setSoTimeout(1000 /*ms*/ * 60 /*s*/ * 60 /*m*/);
            try {
                while (!isInterrupted()) {
                    messageXml = in.readUTF();
                    handle((Message) unmarshaller.unmarshal(new StringReader(messageXml)));
                }
            } catch (SocketTimeoutException e) { // client disconnected
                String infoMessage = "The client";
                if (client != null) {
                    infoMessage = buildMessage(infoMessage, "(id", client.getClientId(), ')');
                }
                infoMessage = buildMessage(infoMessage, "disconnected (address"
                        , socket.getRemoteSocketAddress(), ')');
                LOGGER.info(infoMessage);
                if (client != null && !client.save()) {
                    LOGGER.warn(buildMessage("Saving the client (id", client.getClientId()
                            , ") has not been completed properly"));
                }
            } catch (IOException e) {
                String infoMessage = "Client";
                if (logged) {
                    infoMessage = buildMessage(infoMessage, "(id", client.getClientId(), ")");
                }
                infoMessage = buildMessage(infoMessage, "disconnected (address"
                        , socket.getRemoteSocketAddress(), ')');
                LOGGER.trace(infoMessage);
                if (client != null && !client.save()) {
                    LOGGER.warn(buildMessage("Saving the client (id", client.getClientId()
                            , ") has not been completed properly"));
                }
            }
        } catch (JAXBException e) { // unknown error
            LOGGER.fatal(e.getLocalizedMessage());
        } catch (SocketException e) {
            LOGGER.error(e.getLocalizedMessage());
        } finally { // TODO tracking in which case the thread begins to execute this code (in order to prevent double-saving)
            interrupt();
        }
    }

    /**
     *  This methods may inform if the message is from current client
     *
     * @param           message a {@code Message} to be checked
     *
     * @return          {@code true} if and only if the client has logged in and his {@code clientId}
     *                  is equal to {@code fromId} of the {@code message}, {@code false otherwise}
     * */
    private boolean isMessageFromThisLoggedClient(Message message) {
        if (message == null) {
            LOGGER.error("Passed null-message value to check the addresser id");
            return false;
        }
        if (!logged) {
            LOGGER.trace("Passed message to check before log-in: ".concat(message.toString()));
            return false;
        }
        if (message.getFromId() == null || message.getFromId() != client.getClientId()) {
            LOGGER.info(buildMessage("Expected to receive clientId", client.getClientId()
                    , "but found", message.getFromId()));
            return false;
        }
        return true;
    }

    private void handle(Message message) {
        Message responseMessage = new Message(MessageStatus.ERROR)
                .setText("This is a default text. If you got this message, that means that something went wrong.");
        try {
            switch (message.getStatus()) {
                case AUTH:
                    responseMessage = auth(message);
                    break;
                case REGISTRATION:
                    responseMessage = registration(message);
                    break;
                case MESSAGE:
                    responseMessage = sendMessage(message);
                    break;
                case CLIENTBAN:
                    responseMessage = clientBan(message);
                    break;
                case CREATE_ROOM:
                    if (!isMessageFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    responseMessage = createRoom(message);
                    break;
                case DELETE_ROOM:
                    if (!isMessageFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    break;
                case INVITE_USER:
                    if (!isMessageFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    break;
                case UNINVITE_USER:
                    if (!isMessageFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                case STOP_SERVER:
                    responseMessage = stopServer(message);
                    if (MessageStatus.ACCEPTED.equals(responseMessage.getStatus())) {
                        LOGGER.trace("Interrupting the server");
                        server.interrupt();
                    }
                    break;
                case ROOM_LIST:
                    if (logged) {
                        responseMessage = getRooms();
                    } else {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Has not been logged");
                    }
                    break;
                case FRIEND_LIST:
                    if (logged) {
                        responseMessage = getFriends();
                    } else {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Has not been logged");
                    }
                    break;
                case CLIENTUNBAN:
                    responseMessage = clientUnban(message);
                    break;
                case RESTART_SERVER:
                    try {
                        ServerProcessing.restartServer(server.getConfig());
                    } catch (InvalidPropertiesFormatException e) {
                        LOGGER.error(e.getClass().getName().concat(" occurred"));
                        throw new RuntimeException(e);
                    }
                case ROOM_MEMBERS:
                    responseMessage = getRoomMembers(message);
                    break;
                case MESSAGE_HISTORY:
                    responseMessage = getRoomMessages(message);
                    break;
                case CLIENT_INFO:
                    responseMessage = getClientInfo(message);
                    break;
                default:
                    responseMessage = new Message(MessageStatus.ERROR)
                            .setText(buildMessage("Unknown message status", message.getStatus().toString()));
            }
        } finally {
            sendMessageToConnectedClient(responseMessage);
            LOGGER.trace("Message has been sent");
            if (MessageStatus.REGISTRATION.equals(message.getStatus())
                    && MessageStatus.ACCEPTED.equals(responseMessage.getStatus())) {
                sendMessageToConnectedClient(new Message(MessageStatus.KICK).setText("Please, re-login on the server"));
                interrupt();
            }
        }
    }

    private Message getClientInfo(Message message) {
        if (!isMessageFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Log in first");
        }
        if (message.getToId() == null) {
            return new Message(MessageStatus.ERROR).setText("Unspecified client id");
        }
        if (!ServerProcessing.hasAccountBeenRegistered(server.getConfig(), message.getToId())) {
            return new Message(MessageStatus.ERROR).setText("Unable to find the specified client")
                    .setToId(message.getToId());
        }
        Client client = loadClient(server.getConfig(), message.getToId());
        return new Message(MessageStatus.ACCEPTED).setText(client.getName()).setFromId(client.getClientId());
    }

    private Message addFriend(Message message) {
        if (message == null) {
            LOGGER.warn("null message passed");
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        if (!isMessageFromThisLoggedClient(message)) {
            LOGGER.warn("null toId passed");
            return new Message(MessageStatus.DENIED).setText("Wrong addresser");
        }
        if (message.getToId() == null) {
            LOGGER.warn("null toId passed");
            return new Message(MessageStatus.ERROR).setText("Wrong addressee");
        }
        int toId = message.getToId();
        if (!ServerProcessing.hasAccountBeenRegistered(server.getConfig(), toId)) {
            LOGGER.trace(new StringBuilder("Unable to find a client (id ").append(toId).append(")"));
            return new Message(MessageStatus.ERROR)
                    .setText(buildMessage("The client (id", toId, ") has not been found"));
        }
        Client client;
        if (server.getOnlineClients().containsKey(toId)) {
            client = server.getOnlineClients().get(toId).getClient();
        } else {
            client = loadClient(server.getConfig(), toId);
        }
        if (client.getFriends().contains(this.client.getClientId())) {
            return new Message(MessageStatus.DENIED).setText("You are already friends").setFromId(message.getToId())
                    .setToId(message.getFromId());
        }
        client.getFriends().add(this.client.getClientId());
        this.client.getFriends().add(client.getClientId());
        client.save();
        return new Message(MessageStatus.ACCEPTED).setText("Client are friends now").setFromId(message.getToId())
                .setToId(message.getFromId());
    }

    private Message stopServer(@NotNull Message message) {
        if (!MessageStatus.STOP_SERVER.equals(message.getStatus())) {
            String errorMessage = buildMessage("Message of status", MessageStatus.STOP_SERVER
                    , "was expected, but found", message.getStatus());
            LOGGER.warn(errorMessage);
            return new Message(MessageStatus.ERROR).setText("Internal error: ".concat(errorMessage));
        }
        if (!server.getConfig().getProperty("server_login").equals(message.getLogin())
                || !server.getConfig().getProperty("server_password").equals(message.getPassword())) {
            return new Message(MessageStatus.DENIED).setText("Please, check your login and password");
        }
        return new Message(MessageStatus.ACCEPTED).setText("Server is going to shut down");
    }

    /**
     *  The method {@code sendMessage} sends an instance of {@code Message} of the {@code MessageStatus.MESSAGE}
     * to the certain {@code Room}
     *  It is expected, that {@code message} contains (at least) set following parameters :
     *      {@code fromId}
     *      {@code roomId}
     *      {@code text}
     *
     * @param           message a {@code Message} to be sent
     *
     * @return          an instance of {@code Message} containing information about the operation execution
     *                  it may be of {@code MessageStatus.ERROR} either {@code MessageStatus.ACCEPTED}
     *                  or {@code MessageStatus.DENIED} status
     * */
    private Message sendMessage(Message message) {
        if (message == null) {
            LOGGER.error("Message is null");
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        if (message.getText() == null) {
            return new Message(MessageStatus.ERROR).setText("Message text has not been set");
        }
        if (message.getFromId() == null) {
            return new Message(MessageStatus.ERROR).setText("Addresser's id has not been set");
        }
        if (message.getRoomId() == null) {
            return new Message(MessageStatus.ERROR).setText("The room id is not set");
        }
        Message responseMessage;
        try {
            RoomProcessing.sendMessage(server, message);
            responseMessage = new Message(MessageStatus.ACCEPTED);
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            responseMessage = new Message(MessageStatus.ERROR).setText("An internal error occurred");
        } catch (RoomNotFoundException e) {
            LOGGER.trace(buildMessage("Room id", message.getRoomId(), "has not been found"));
            responseMessage = new Message(MessageStatus.ERROR).setText("Unable to find the room having the specified id");
        }
        return responseMessage;
    }

    /**
     *  The method that turns an incoming connection to a client's session
     * Verifies the {@code message} of status {@code MessageStatus.AUTH} comparing the incoming user data
     * such as a login and a password.
     *
     * @param           message a message of {@code MessageStatus.AUTH} containing a login and a password
     *
     * @throws          IOException if one or more of the fields {@code fromId}, {@code login} or {@code password}
     *                  are not specified. Also it is thrown in cases when {@code message} is not of
     *                  status {@code MessageStatus.AUTH}
     *
     * @exception       IllegalPasswordException in case if the password from the {@code message}
     *                  does not match the one from the userfile
     *
     * @exception       ClientNotFoundException if the specified client's file has not been found
     *                  in the {@code clientsDir} folder or there is not user data file
     *
     * @exception       NullPointerException in case when message equals {@code null}
     *
     * */
    private Message auth(Message message) {
        if (message == null) {
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        if (!MessageStatus.AUTH.equals(message.getStatus())) {
            String errorMessage = buildMessage("Message of the", MessageStatus.AUTH, "was expected but found"
                    , message.getStatus().toString());
            LOGGER.warn(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (message.getLogin() == null || message.getPassword() == null) {
            return new Message(MessageStatus.ERROR)
                    .setText((message.getLogin() == null ? "Login" : "Password").concat(" must be set"));
        }
        if (message.getFromId() != null) {
            return new Message(MessageStatus.ERROR).setText("Registration request must not have set fromId");
        }
        File clientFolder = new File(server.getClientsDir(), String.valueOf(message.getLogin().hashCode()));
        File clientFile = new File(clientFolder, String.valueOf(message.getLogin().hashCode()).concat(".xml"));
        if (!clientFile.isFile()) {
            return new Message(MessageStatus.DENIED).setText("Please, check your password and login");
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Client client = (Client) unmarshaller.unmarshal(clientFile);
            if (client.isBaned()) {
                if (LocalDateTime.now().isBefore(client.getIsBannedUntill())) {
                    return new Message(MessageStatus.DENIED).setText(buildMessage("You are banned until"
                            , ServerProcessing.DATE_TIME_FORMATTER.format(client.getIsBannedUntill())));
                } else {
                    client.setBaned(false);
                    client.setIsBannedUntill(null);
                    client.save();
                    LOGGER.trace(buildMessage("Client (id", client.getClientId(),
                            ") has been unbanned automatically (ban period is over)"));
                }
            }
            logged = client.getPassword().equals(message.getPassword());
            if (logged) {
                this.client = client;
                this.client.setServer(server);
                LOGGER.trace(buildMessage("Client id", client.getClientId(), "has logged in"));
                return new Message(MessageStatus.ACCEPTED);
            } else {
                LOGGER.trace("Wrong password from client id ".concat(String.valueOf(client.getClientId())));
                return new Message(MessageStatus.DENIED).setText("Please, check your password and login");
            }
        } catch (JAXBException e) {
            LOGGER.fatal(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
    }

    private Message registration(Message message) {
        if(message == null) {
            return new Message(MessageStatus.ERROR).setText("Message came as null");
        }
        if (!MessageStatus.REGISTRATION.equals(message.getStatus())) {
            return new Message(MessageStatus.ERROR).setText(buildMessage("Message of the",
                    MessageStatus.REGISTRATION, "was expected but found", message.getStatus()));
        }
        File clientsDir = new File(server.getConfig().getProperty("clientsDir"));
        String login = message.getLogin();
        String password = message.getPassword();
        if (login == null || password == null) {
            return new Message(MessageStatus.ERROR).setText((login == null ? "login" : "password")
                    .concat(" has not been set"));
        }
        File clientDir = new File(clientsDir, String.valueOf(login.hashCode()));
        File clientFile = new File(clientDir, clientDir.getName().concat(".xml"));
        if (clientDir.isDirectory()) {
            return new Message(MessageStatus.DENIED)
                    .setText(buildMessage("The login", login, "is already taken"));
        }
        try {
            if (!clientDir.mkdir() || !clientFile.createNewFile()) {
                throw new IOException();
            }
        } catch (IOException e) {
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        Client client = new Client();
        try {
            client.setLogin(login);
            client.setServer(server);
            client.setPassword(password);
            client.setName(message.getText() == null ? login : message.getText());
            client.setClientId(login.hashCode());
            client.getRooms().add(0);
            Room commomChat = RoomProcessing.loadRoom(server, 0);
            commomChat = server.getOnlineRooms().get(0);
            commomChat.getMembers().add(client.getClientId());
            commomChat.save();
        } catch (NullPointerException e) {
            return new Message(MessageStatus.ERROR)
                    .setText("Check whether you have specified all the necessary parameters");
        } catch (InvalidPropertiesFormatException e) {
            LOGGER.error("Wrong properties");
            throw new RuntimeException(e);
        }
        if (!server.getOnlineRooms().containsKey(0)) {
            try {
                RoomProcessing.loadRoom(server, 0);
            } catch (InvalidPropertiesFormatException e) {
                LOGGER.error(buildMessage("Unknown server configuration error occurred", e.getMessage()));
                throw new RuntimeException(e);
            }
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(client, clientFile);
            LOGGER.info(buildMessage("New client id", client.getClientId(), "has been registered"));
        } catch (JAXBException  e) {
            LOGGER.error(e.getLocalizedMessage());
        }
        return new Message(MessageStatus.ACCEPTED)
                .setText(buildMessage("The account", login, "has been successfully created"));
    }

    /**
     *  The method {@code createRoom} handles with an input request representing by {@code Message}
     * having {@code MessageStatus.CREATE_ROOM} status
     *
     * @param           message a command that contains the {@code clientId} of a creator
     *
     * @return          an instance of {@code Message} that informs whether new room was created or not
     * */
    private Message createRoom(@NotNull Message message) {
        if (!isMessageFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Please, log in first");
        }
        if (!MessageStatus.CREATE_ROOM.equals(message.getStatus())) {
            LOGGER.error(new StringBuilder("Unexpected message status. Expected ").append(MessageStatus.CREATE_ROOM)
                    .append(". But found ").append(message.getStatus()));
            return new Message(MessageStatus.ERROR)
                    .setText(new StringBuilder("The message status must be ").append(MessageStatus.CREATE_ROOM)
                            .append(" but found ").append(message.getStatus()).toString());
        }
        /*
        *   The field toId is considered as an id of the initial room member, thus it must be valid
        * i.e. the client with such id must exists
        * */
        try {
            Room room = RoomProcessing.createRoom(server, message.getFromId());
            if (room == null) {
                return new Message(MessageStatus.ERROR).setText("Some error has occurred during the room creation");
            } else {
                client.getRooms().add(room.getRoomId());
                LOGGER.trace(new StringBuilder("New room (id ").append(room.getRoomId()).append(") has been created"));
                return new Message(MessageStatus.ACCEPTED).setRoomId(room.getRoomId())
                        .setText(new StringBuilder("The room id: ").append(room.getRoomId())
                                .append(" has been successfully created").toString());
            }
        } catch (InvalidPropertiesFormatException e) { // error while room creation
            LOGGER.error(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText("Internal has error occurred");
        }
    }

    public void sendMessageToConnectedClient(Message message) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter stringWriter = new StringWriter();
            marshaller.marshal(message, stringWriter);
            out.writeUTF(stringWriter.toString());
            out.flush();
        } catch (IOException | JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
    }

    /**
     *  The method that informs if there is a member {@code clientId} in the room {@code roomId}
     * on server denoted by {@code serverProperties}
     *
     * @param           serverProperties a set of the server configurations
     * @param           clientId The client's clientId to be searched for
     * @param           roomId The room clientId where {@code clientId} will be searched
     *
     * @return          {@code true} if and only if the server denoted by this {@code serverProperties} exists
     *                  and there is a room id {@code roomId} with specified client id {@code clientId}
     * */
    public static boolean isMember(@NotNull Properties serverProperties, int clientId, int roomId) {
        if (!ServerProcessing.arePropertiesValid(serverProperties)
                || RoomProcessing.hasRoomBeenCreated(serverProperties, roomId) == 0L
                || ServerProcessing.hasAccountBeenRegistered(serverProperties,clientId)) {
            return false;
        }
        XPath xPath = XPathFactory.newInstance().newXPath();
        XPathExpression xPathExpression = null;
        try {
            xPathExpression = xPath.compile("room/members/clientId");
        } catch (XPathExpressionException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
        File roomsDir = new File(serverProperties.getProperty("roomsDir"));
        File roomDir = new File(roomsDir, String.valueOf(roomId));
        File roomFile = new File(roomDir, String.valueOf(roomId).concat(".xml"));
        try {
            NodeList resultNodeList = (NodeList) xPathExpression.evaluate(
                    new InputSource(new BufferedReader(new FileReader(roomFile))), XPathConstants.NODESET);
            for(int i = 0; i < resultNodeList.getLength(); i++) {
                if(clientId == Integer.parseInt(resultNodeList.item(i).getTextContent())) {
                    return true;
                }
            }
        } catch (FileNotFoundException | XPathExpressionException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false; // return false OR throw new RuntimeException(e); ?
        }
        return false;
    }

    /**
     * The method {@code clientExists} informs whether there is such client registered on the server
     *
     *  NOTE! This method will not inform you in case if you enter invalid properties, {@code null} or
     * some kind of exception has occurred. It will just return {@code false} if it failed to
     * get the client's parameters.
     *
     * @param           serverProperties a set of server configurations
     * @param           clientId client's id to be searched
     *
     * @return          {@code true} if and only if the server denoted by {@code serverProperties} exists
     *                  a client with such {@code clientId} has been registered on the server
     * */
    public static boolean clientExists(Properties serverProperties, int clientId) {
        try {
            if (!ServerProcessing.arePropertiesValid(serverProperties)) {
                return false;
            }
            File clientsFolder = new File(serverProperties.getProperty("clientsDir"));
            File clientFolder = new File(clientsFolder, String.valueOf(clientId));
            File clientFile = new File(clientFolder, clientFolder.getName().concat(".xml"));
            return clientFile.isFile();
        } catch (Throwable e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
    }

    private static Client loadClient(Properties serverProperties, int clientId) {
        if (!clientExists(serverProperties, clientId)) {
            throw new ClientNotFoundException(clientId);
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            File clientsFolder = new File(serverProperties.getProperty("clientsDir"));
            File clientFolder = new File(clientsFolder, String.valueOf(clientId));
            File clientFile = new File(clientFolder, clientFolder.getName().concat(".xml"));
            return (Client) unmarshaller.unmarshal(clientFile);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *  The method {@code clientBan} handles with requests of blocking a user.
     *
     * @param           message an instance of {@code Message} that represents a request about blocking a user.
     *                  NOTE! It is expected that message contains following non-null fields
     *                          1) {@code fromId} - id of registered user who has admin rights
     *                              i.e. an instance of {@code Client} representing his account
     *                              has {@code isAdmin == true}
     *                          2)  {@code toId} - id of registered client who does not have admin
     *                              rights and is not banned
     *                          3)  {@code text} - a text representation of a {@code LocalDateTime} instance that points
     *                              the end of the ban (expected to be a future timestamp).
     *                              NOTE! It must be formatted using ServerProcessing.DATE_TIME_FORMATTER
     *
     * @return          an instance of {@code Message} that contains info about performed (or not) operation.
     *                  It may be of the following statuses
     *                      {@code MessageStatus.ACCEPTED}  -   if the specified client has been banned
     *                      {@code MessageStatus.DENIED}    -   if the specified client is an admin,
     *                                                          is already banned or the client who sent this request
     *                                                          does not have admin rights
     *                      {@code MessageStatus.ERROR}     -   if an error occurred while executing the operation
     * */
    private Message clientBan(@NotNull Message message) {
        String errorMessage;
        StringBuilder errorMessageBuilder;
        if (message == null) {
            LOGGER.error("Passed null-message to perform client ban");
            return new Message(MessageStatus.ERROR).setText("Error occurred while banning(null)");
        }
        if (message.getToId() == null) {
            errorMessage = new StringBuilder("Attempt to ban unspecified account from ")
                    .append(message.getFromId() != null ? message.getFromId() : " unspecified client").toString();
            LOGGER.trace(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (!isMessageFromThisLoggedClient(message) && !(
                server.getConfig().getProperty("server_login").equals(message.getLogin())
                        && server.getConfig().getProperty("server_password").equals(message.getPassword())
                )) {
            errorMessage = "Wrong fromId or client has not log in";
            LOGGER.trace(errorMessage);
            return new Message(MessageStatus.DENIED).setText(errorMessage);
        }
        int toId = message.getToId();
        Integer fromId = message.getFromId();
        if (message.getText() == null) {
            errorMessageBuilder = new StringBuilder("Attempt to ban client without specifying the term");
            errorMessage = errorMessageBuilder.toString();
            LOGGER.trace(errorMessageBuilder
                    .append(" from ").append(message.getFromId()).append(" to ").append(message.getToId()).toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        LocalDateTime bannedUntil;
        try {
            bannedUntil = LocalDateTime.parse(message.getText(), ServerProcessing.DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            errorMessageBuilder = new StringBuilder("Invalid (unparseable) end data of ban has been set: ")
                    .append(message.getText());
            errorMessage = errorMessageBuilder.toString();
            errorMessageBuilder.append(" from ")
                    .append(fromId == null ? "server admin" : fromId)
                    .append(" to ").append(toId);
            LOGGER.trace(errorMessageBuilder.toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (LocalDateTime.now().isAfter(bannedUntil)) {
            errorMessageBuilder = new StringBuilder("Invalid (past) end data of ban has been set: ").append(bannedUntil);
            errorMessage = errorMessageBuilder.toString();
            errorMessageBuilder.append(" from ")
                    .append(fromId == null ? "server admin" : fromId)
                    .append(" to ").append(toId);
            LOGGER.trace(errorMessageBuilder.toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        Client clientIsBeingBanned;
        try {
            if (server.getOnlineClients().containsKey(toId)) {
                clientIsBeingBanned = server.getOnlineClients().get(toId).getClient();
            } else {
                clientIsBeingBanned = loadClient(server.getConfig(), toId);
            }
        } catch (ClientNotFoundException e) {
            errorMessageBuilder = new StringBuilder("Client id ").append(e.getClientId()).append(" has not been found");
            LOGGER.error(errorMessageBuilder.toString());
            return new Message(MessageStatus.ERROR).setText(errorMessageBuilder.toString());
        }
        clientIsBeingBanned.setServer(server);
        boolean isAdmin = true;
        if (message.getFromId() != null) {
            isAdmin = server.getOnlineClients().get(message.getFromId()).getClient().isAdmin();
        }
        boolean isAlreadyBanned = clientIsBeingBanned.isBaned();
        boolean isBeingBannedAdmin = clientIsBeingBanned.isAdmin();
        if (!isAdmin || isBeingBannedAdmin || isAlreadyBanned || bannedUntil.isBefore(LocalDateTime.now())) {
            String deniedMessage = "Not enough rights to perform this operation: ".concat(
                    (!isAdmin || isBeingBannedAdmin) ?  "not enough rights" :
                    (isAlreadyBanned) ? "the specified client is already banned" : "invalid date");
            LOGGER.trace(deniedMessage);
            return new Message(MessageStatus.DENIED).setText(deniedMessage);
        }
        if (server.getOnlineClients().containsKey(message.getToId())) {
            server.getOnlineClients().get(message.getToId()).interrupt();
        }
        clientIsBeingBanned.setBaned(true);
        clientIsBeingBanned.setIsBannedUntill(bannedUntil);
        if (clientIsBeingBanned.save()) {
            return new Message(MessageStatus.ACCEPTED)
                    .setText(buildMessage("The client id", toId, "has been banned"));
        } else {
            errorMessage = buildMessage("Unknown error. The client (id", clientIsBeingBanned.getClientId()
                    , ") has not been banned");
            LOGGER.warn(errorMessage );
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
    }

    /**
     *  The method {@code userUnban} handles with requests of unblocking a user.
     *
     * @param           message an instance of {@code Message} that represents a request about blocking a user.
     *                  NOTE! It is expected that message contains following non-null fields
     *                      1) {@code fromId} - id of registered user who has admin rights
     *                          i.e. an instance of {@code Client} representing his account
     *                          has {@code isAdmin == true}
     *                      2)  {@code toId} - id of registered client who is currently banned
     *
     * @return          an instance of {@code Message} that contains info about performed (or not) operation.
     *                  It may be of the following statuses:
     *                      {@code MessageStatus.ACCEPTED}  -   if the specified client has been unbanned
     *                      {@code MessageStatus.DENIED}    -   if the sender is not an admin or specified client
     *                                                          is not currently banned
     *                      {@code MessageStatus.ERROR}     -   if an error occurred while executing the operation
     * */
    private Message clientUnban(Message message) {
        String errorMessage;
        if (message == null) {
            LOGGER.error("Passed null-message to perform client unbanning");
            return new Message(MessageStatus.ERROR).setText("Error occurred while unbanning (null message)");
        }
        if (message.getToId() == null) {
            errorMessage = "Attempt to unban unspecified account";
            LOGGER.trace(buildMessage(errorMessage ,"from"
                    , message.getFromId() != null ? message.getFromId() : "unspecified client"));
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        int toId = message.getToId();
        if (!isMessageFromThisLoggedClient(message) && !(
                server.getConfig().getProperty("server_login").equals(message.getLogin())
                        && server.getConfig().getProperty("server_password").equals(message.getPassword())
        )) {
            errorMessage = buildMessage("Attempt to perform an action before log-in :", message);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        Integer fromId = message.getFromId();
        if (!ServerProcessing.hasAccountBeenRegistered(server.getConfig(), toId)) {
            errorMessage = buildMessage("Attempt to unban unregistered client from client (admin) (id"
                    , fromId == null ? "server admin" : fromId);
            LOGGER.error(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        boolean isAdmin = true;
        if (fromId != null) {
            isAdmin = loadClient(server.getConfig(), fromId).isAdmin();
        }
        Client clientToUnban = loadClient(server.getConfig(), toId);
        clientToUnban.setServer(server);
        if (!isAdmin) {
            errorMessage = buildMessage("Not enough rights to perform this operation (client id"
                    , fromId, "attempts to unban client id", clientToUnban.getClientId());
            LOGGER.error(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (!clientToUnban.isBaned()) {
            errorMessage = buildMessage("Client", "(id", clientToUnban.getClientId(), ')', "is not banned");
            LOGGER.error(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        clientToUnban.setBaned(false);
        clientToUnban.setIsBannedUntill(null);
        if (clientToUnban.save()) {
            String infoMessage = buildMessage("Client (id", clientToUnban.getClientId()
                    , ") has been unbanned by the admin (id ", (fromId == null ? "server admin" : fromId), ')');
            LOGGER.info(infoMessage);
            return new Message(MessageStatus.ACCEPTED).setText(infoMessage);
        } else {
            LOGGER.warn(buildMessage("The process of unbanning client (id", toId
                    , ") has not been finished properly"));
            return new Message(MessageStatus.ERROR).setText("Unknown error occurred while unbanning");
        }

    }

    @Override
    public void interrupt() {
        if (client != null && !client.save()) {
            LOGGER.error(buildMessage("Saving the client (id", client.getClientId()
                    , ") has not been finished properly"));
        }
        super.interrupt();
    }

    private synchronized Message getFriends() {
        if (!logged) {
            return new Message(MessageStatus.DENIED).setText("Log in first");
        }
        if (client.getFriends().size() == 0) {
            return new Message(MessageStatus.FRIEND_LIST).setText("");
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int clientId : client.getFriends()) {
            stringBuilder.append(clientId).append(',');
        }
        return new Message(MessageStatus.FRIEND_LIST).setText(stringBuilder.substring(0,stringBuilder.length() - 1));
    }

    private synchronized Message getRoomMessages(Message message) {
        if (message == null) {
            return new Message(MessageStatus.ERROR).setText("Internal error occurred");
        }
        if (!isMessageFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Log in first");
        }
        if (message.getRoomId() == null) {
            return new Message(MessageStatus.ERROR).setText("Unspecified room");
        }
        Room room;
        if (!server.getOnlineRooms().containsKey(message.getRoomId())) {
            if (RoomProcessing.hasRoomBeenCreated(server.getConfig(), message.getRoomId()) != 0L) {
                try {
                    room = RoomProcessing.loadRoom(server, message.getRoomId());
                } catch (InvalidPropertiesFormatException | RoomNotFoundException e) {
                    return new Message(MessageStatus.ERROR).setText(e.getLocalizedMessage());
                }
            }
        }
        room = server.getOnlineRooms().get(message.getRoomId());
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            StringWriter stringWriter;
            for (Message roomMessage : room.getMessageHistory()) {
                stringWriter = new StringWriter();
                marshaller.marshal(roomMessage, stringWriter);
                out.writeUTF(stringWriter.toString());
                out.flush();
            }
        } catch (JAXBException | IOException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
        return new Message(MessageStatus.ACCEPTED).setText("This is the end of the room message history")
                .setRoomId(message.getRoomId());
    }

    private synchronized Message getRoomMembers(Message message) {
        if (!logged) {
            return new Message(MessageStatus.DENIED).setText("Log in first");
        }
        if (!isMessageFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Log in first");
        }
        if (message.getRoomId() == null) {
            return new Message(MessageStatus.ERROR).setText("Unset room id");
        }
        if (RoomProcessing.hasRoomBeenCreated(server.getConfig(), message.getRoomId()) != 0L) {
            return new Message(MessageStatus.ERROR).setText("Unable to find a room id"
                    .concat(String.valueOf(message.getRoomId())));
        }
        Room room;
        if (!server.getOnlineRooms().containsKey(message.getRoomId())) {
            try {
                room = RoomProcessing.loadRoom(server, message.getRoomId());
            } catch (InvalidPropertiesFormatException e) {
                LOGGER.error(e.getLocalizedMessage());
                return new Message(MessageStatus.MESSAGE).setText("Server configuration error occurred");
            }
        }
        room = server.getOnlineRooms().get(message.getRoomId());
        if (!room.getMembers().contains(client.getClientId())) {
            return new Message(MessageStatus.DENIED).setRoomId("Not a member of the room").setRoomId(message.getRoomId());
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int clientId : room.getMembers()) {
            stringBuilder.append(clientId).append(',');
        }
        if (stringBuilder.length() == 0) {
            return new Message(MessageStatus.ACCEPTED).setRoomId(message.getRoomId()).setText("");
        }
        return new Message(MessageStatus.ACCEPTED).setRoomId(message.getRoomId())
                .setText(stringBuilder.substring(0,stringBuilder.length() - 1));
    }

    public synchronized Message getRooms() {
        if (client.getRooms().size() == 0) {
            return new Message(MessageStatus.ROOM_LIST).setText("");
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int roomId : client.getRooms()) {
            stringBuilder.append(roomId).append(',');
        }
        return new Message(MessageStatus.ROOM_LIST).setText(stringBuilder.substring(0, stringBuilder.length() - 1));
    }

    private Message inviteClient(Message message) {
        if (message == null) {
            LOGGER.warn("null-message");
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        if (message.getFromId() == null) {
            LOGGER.warn("null fromId");
            return new Message(MessageStatus.ERROR).setText("Missed addresser id");
        }
        if (message.getToId() == null) {
            LOGGER.warn("null toId");
            return new Message(MessageStatus.ERROR).setText("Missed client to be invited");
        }
        if (message.getRoomId() == null) {
            LOGGER.warn("null roomId");
            return new Message(MessageStatus.ERROR).setText("Missed roomId");
        }
        if (!server.getOnlineRooms().containsKey(message.getRoomId())) {
            try {
                server.getOnlineRooms().put(message.getRoomId(), RoomProcessing.loadRoom(server, message.getRoomId()));
            } catch (InvalidPropertiesFormatException e) {
                LOGGER.error(buildMessage("Unknown error", e.getClass().getName(), ' ', e.getMessage()));
                return new Message(MessageStatus.ERROR).setText("Internal error");
            } catch (RoomNotFoundException e) {
                LOGGER.trace(buildMessage("Unable to find a room (id", message.getRoomId(), ')'));
                return new Message(MessageStatus.DENIED)
                        .setText(buildMessage("Unable to find the specified room (id", message.getRoomId(), ')'));
            }
        }
        Room room = server.getOnlineRooms().get(message.getRoomId());
        if (!room.getMembers().contains(message.getFromId())) {
            LOGGER.trace(buildMessage("The client id", message.getFromId()
                    , "is not a member of the room id", message.getRoomId()));
            return new Message(MessageStatus.DENIED).setText("Not a member of the room");
        }
        if (room.getMembers().contains(message.getToId())) {
            LOGGER.trace(buildMessage("Attempt to remove client (id", message.getToId()
                    , ") who is already a member of the room (id", message.getRoomId(), ')'));
            return new Message(MessageStatus.DENIED).setText("This client is already a member of the room");
        }
        room.getMembers().add(message.getToId());
        String infoString = buildMessage("Client (id", message.getToId()
                , ") is a member of the room (id", message.getRoomId(), ')');
        LOGGER.trace(infoString);
        return new Message(MessageStatus.ACCEPTED).setText(infoString);
    }

    private Message uninviteClient(Message message) {
        if (message == null) {
            LOGGER.warn("null-message");
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        if (message.getFromId() == null) {
            LOGGER.warn("null fromId");
            return new Message(MessageStatus.ERROR).setText("Missed addresser id");
        }
        if (message.getToId() == null) {
            LOGGER.warn("null toId");
            return new Message(MessageStatus.ERROR).setText("Missed client to be uninvited");
        }
        if (message.getRoomId() == null) {
            LOGGER.warn("null roomId");
            return new Message(MessageStatus.ERROR).setText("Missed roomId");
        }
        if (!server.getOnlineRooms().containsKey(message.getRoomId())) {
            try {
                server.getOnlineRooms().put(message.getRoomId(), RoomProcessing.loadRoom(server, message.getRoomId()));
            } catch (InvalidPropertiesFormatException e) {
                LOGGER.error(buildMessage("Unknown error", e.getClass().getName(), ' ', e.getMessage()));
                return new Message(MessageStatus.ERROR).setText("Internal error");
            } catch (RoomNotFoundException e) {
                LOGGER.trace(buildMessage("Unable to find a room (id", message.getRoomId(), ')'));
                return new Message(MessageStatus.DENIED)
                        .setText(buildMessage("Unable to find the specified room (id"
                                , message.getRoomId(), ')'));
            }
        }
        Room room = server.getOnlineRooms().get(message.getRoomId());
        if (!room.getMembers().contains(message.getFromId())) {
            LOGGER.trace(buildMessage("The client (id", message.getFromId()
                    , ") is not a member of the room id", message.getRoomId()));
            return new Message(MessageStatus.DENIED).setText("Not a member of the room");
        }
        if (!room.getMembers().contains(message.getToId())) {
            LOGGER.trace(buildMessage("Attempt to remove client (id", message.getToId()
                    , ") who is not a member of the room (id", message.getRoomId(), ')'));
            return new Message(MessageStatus.DENIED).setText("This client is not a member of the room");
        }
        room.getMembers().remove(message.getToId());
        String infoString = buildMessage("Client (id", message.getToId()
                , ") is not a member of the room (id", message.getRoomId(), ')');
        LOGGER.trace(infoString);
        return new Message(MessageStatus.ACCEPTED).setText(infoString);
    }
}