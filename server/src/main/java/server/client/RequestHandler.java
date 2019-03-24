package server.client;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import server.exceptions.ClientNotFoundException;
import server.exceptions.RoomNotFoundException;
import server.processing.ClientProcessing;
import server.processing.RestartingEnvironment;
import server.processing.ServerProcessing;
import server.room.Room;
import server.room.RoomProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.InvalidPropertiesFormatException;

import static common.Utils.buildMessage;
import static server.processing.ClientProcessing.loadClient;

/**
 *  The {@code RequestHandler} class contains the set of methods that take client messages, operate them
 * and, in most cases return response messages
 *
 * @see ClientListener
 * */
@SuppressWarnings("CanBeFinal")
class RequestHandler {
    private ClientListener clientListener;
    private static Logger LOGGER = Logger.getLogger(RequestHandler.class.getSimpleName());

    void handle(Message message) {
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
                    if (isMessageNotFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    responseMessage = createRoom(message);
                    break;
                case DELETE_ROOM:
                    if (isMessageNotFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    break;
                case INVITE_USER:
                    if (isMessageNotFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    } else {
                        responseMessage = addClientToRoom(message);
                    }
                    break;
                case UNINVITE_USER:
                    if (isMessageNotFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    } else {
                        responseMessage = kickClientFromRoom(message);
                    }
                case STOP_SERVER:
                    responseMessage = stopServer(message);
                    if (MessageStatus.ACCEPTED.equals(responseMessage.getStatus())) {
                        LOGGER.trace("Interrupting the server");
                        clientListener.getServer().interrupt();
                    }
                    break;
                case ROOM_LIST:
                    if (clientListener.isLogged()) {
                        responseMessage = getRooms();
                    } else {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Has not been logged");
                    }
                    break;
                case CLIENTUNBAN:
                    responseMessage = clientUnban(message);
                    break;
                case RESTART_SERVER:
                    responseMessage = restartServer(message);
                    break;
                case ROOM_MEMBERS:
                    responseMessage = getRoomMembers(message);
                    break;
                case MESSAGE_HISTORY:
                    responseMessage = getRoomMessages(message);
                    break;
                case GET_CLIENT_NAME:
                    responseMessage = getClientName(message);
                    break;
                case GET_ROOM_MEMBERS:
                    responseMessage = getRoomMembers(message);
                    break;
                default:
                    responseMessage = new Message(MessageStatus.ERROR)
                            .setText(buildMessage("Unknown message status", message.getStatus().toString()));
            }
        } finally {
            clientListener.sendMessageToConnectedClient(responseMessage);
            LOGGER.trace("Message has been sent");
            if (MessageStatus.REGISTRATION.equals(message.getStatus())
                    && MessageStatus.ACCEPTED.equals(responseMessage.getStatus())) {
                clientListener.sendMessageToConnectedClient(new Message(MessageStatus.KICK)
                        .setText("Please, re-login on the server"));
                clientListener.interrupt();
            }
        }
    }

    RequestHandler(ClientListener clientListener) {
        this.clientListener = clientListener;
    }

    /**
     * This methods may inform if the message is from current client
     *
     * @param message a {@code Message} to be checked
     * @return {@code true} if and only if the client has logged in and his {@code clientId}
     * is equal to {@code fromId} of the {@code message}, {@code false otherwise}
     */
    private boolean isMessageNotFromThisLoggedClient(Message message) {
        if (message == null) {
            LOGGER.error("Passed null-message value to check the addresser id");
            return true;
        }
        if (!clientListener.isLogged()) {
            LOGGER.trace("Passed message to check before log-in: ".concat(message.toString()));
            return true;
        }
        if (message.getFromId() == null || message.getFromId() != clientListener.getClient().getClientId()) {
            LOGGER.info(buildMessage("Expected to receive clientId", clientListener.getClient().getClientId()
                    , "but found", message.getFromId()));
            return true;
        }
        return false;
    }

    /**
     * The method that turns an incoming connection to a client's session
     * Verifies the {@code message} of status {@code MessageStatus.AUTH} comparing the incoming user data
     * such as a login and a password.
     *
     * @param message a message of {@code MessageStatus.AUTH} containing a login and a password
     *
     * @throws ClientNotFoundException  if the specified client's file has not been found
     *                                  in the {@code clientsDir} folder or there is not user data file
     * @throws NullPointerException     in case when message equals {@code null}
     */
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
        File clientFolder = new File(clientListener.getServer().getClientsDir()
                , String.valueOf(message.getLogin().hashCode()));
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
                    client.setIsBannedUntil(null);
                    client.save();
                    LOGGER.trace(buildMessage("Client (id", client.getClientId(),
                            ") has been unbanned automatically (ban period is over)"));
                }
            }
            clientListener.setLogged(client.getPassword().equals(message.getPassword()));
            if (clientListener.isLogged()) {
                clientListener.setClient(client);
                clientListener.getClient().setServer(clientListener.getServer());
                LOGGER.trace(buildMessage("Client (id", client.getClientId(), ") has logged in"));
                return new Message(MessageStatus.ACCEPTED);
            } else {
                if (LOGGER.isEnabledFor(Level.TRACE)) {
                    LOGGER.trace(buildMessage("Wrong password from client (id"
                            , String.valueOf(client.getClientId())));
                }
                return new Message(MessageStatus.DENIED).setText("Please, check your password and login");
            }
        } catch (JAXBException e) {
            LOGGER.fatal(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
    }

    /**
     * The method {@code clientBan} handles with requests of blocking a user.
     *
     * @param message an instance of {@code Message} that represents a request about blocking a user.
     *                NOTE! It is expected that message contains following non-null fields
     *                1) {@code fromId} - id of registered user who has admin rights
     *                i.e. an instance of {@code Client} representing his account
     *                has {@code isAdmin == true}
     *                2)  {@code toId} - id of registered client who does not have admin
     *                rights and is not banned
     *                3)  {@code text} - a text representation of a {@code LocalDateTime} instance that points
     *                the end of the ban (expected to be a future timestamp).
     *                NOTE! It must be formatted using ServerProcessing.DATE_TIME_FORMATTER
     * @return an instance of {@code Message} that contains info about performed (or not) operation.
     * It may be of the following statuses
     * {@code MessageStatus.ACCEPTED}  -   if the specified client has been banned
     * {@code MessageStatus.DENIED}    -   if the specified client is an admin,
     * is already banned or the client who sent this request
     * does not have admin rights
     * {@code MessageStatus.ERROR}     -   if an error occurred while executing the operation
     */
    private Message clientBan(@NotNull Message message) {
        String errorMessage;
        if (message.getToId() == null) {
            errorMessage = buildMessage("Attempt to ban unspecified account from "
                    , message.getFromId() != null ? message.getFromId() : " unspecified client");
            LOGGER.trace(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (isMessageNotFromThisLoggedClient(message) && !(
                clientListener.getServer().getConfig().getProperty("serverLogin").equals(message.getLogin())
                        && clientListener.getServer().getConfig().getProperty("serverPassword")
                        .equals(message.getPassword())
        )) {
            errorMessage = "Wrong fromId or client has not log in";
            LOGGER.trace(errorMessage);
            return new Message(MessageStatus.DENIED).setText(errorMessage);
        }
        int toId = message.getToId();
        if (message.getText() == null) {
            errorMessage = "Attempt to ban client without specifying the term";
            LOGGER.trace(buildMessage(errorMessage, "from", message.getFromId(), "to", message.getToId()));
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        LocalDateTime bannedUntil;
        try {
            bannedUntil = LocalDateTime.parse(message.getText(), ServerProcessing.DATE_TIME_FORMATTER);
            if (LocalDateTime.now().isAfter(bannedUntil)) {
                throw new DateTimeException(
                        buildMessage("Passed the past date of the ban end:", message.getText()));
            }
        } catch (DateTimeException e) {
            errorMessage = buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage());
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(
                        buildMessage(errorMessage
                                , ". From id", message.getFromId(), "to id", message.getToId()));
            }
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        Client clientIsBeingBanned;
        try {
            if (clientListener.getServer().getOnlineClients().safe().containsKey(toId)) {
                clientIsBeingBanned = clientListener.getServer().getOnlineClients().safe().get(toId).getClient();
            } else {
                clientIsBeingBanned = loadClient(clientListener.getServer().getConfig(), toId);
            }
        } catch (ClientNotFoundException e) {
            errorMessage = buildMessage("Client (id", e.getClientId(), "has not been found");
            if (LOGGER.isEnabledFor(Level.ERROR)) {
                LOGGER.error(errorMessage);
            }
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        clientIsBeingBanned.setServer(clientListener.getServer());
        boolean isAdmin = true;
        if (message.getFromId() != null) {
            isAdmin = clientListener.getServer().getOnlineClients().safe()
                    .get(message.getFromId()).getClient().isAdmin();
        }
        boolean isAlreadyBanned = clientIsBeingBanned.isBaned();
        boolean isBeingBannedAdmin = clientIsBeingBanned.isAdmin();
        if (!isAdmin || isBeingBannedAdmin || isAlreadyBanned || bannedUntil.isBefore(LocalDateTime.now())) {
            String deniedMessage = "Not enough rights to perform this operation: ".concat(
                    (!isAdmin || isBeingBannedAdmin) ? "not enough rights" :
                            (isAlreadyBanned) ? "the specified client is already banned" : "invalid date");
            LOGGER.trace(deniedMessage);
            return new Message(MessageStatus.DENIED).setText(deniedMessage);
        }
        if (clientListener.getServer().getOnlineClients().safe().containsKey(message.getToId())) {
            clientListener.getServer().getOnlineClients().safe().get(message.getToId()).interrupt();
        }
        clientIsBeingBanned.setBaned(true);
        clientIsBeingBanned.setIsBannedUntil(bannedUntil);
        if (clientIsBeingBanned.save()) {
            return new Message(MessageStatus.ACCEPTED)
                    .setText(buildMessage("The client id", toId, "has been banned"));
        } else {
            errorMessage = buildMessage("Unknown error. The client (id", clientIsBeingBanned.getClientId()
                    , ") has not been banned");
            LOGGER.warn(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
    }

    private Message restartServer(Message message) {
        if ((isMessageNotFromThisLoggedClient(message))
                && !message.getLogin().equals(clientListener.getServer().getConfig().getProperty("serverLogin"))
                && !message.getPassword().equals(
                        clientListener.getServer().getConfig().getProperty("serverPassword"))) {
            return new Message(MessageStatus.DENIED).setText("Log in first");
        }
        if (clientListener.isLogged() && !clientListener.getClient().isAdmin()) {
            return new Message(MessageStatus.DENIED).setText("Not enough rights to perform the restart");
        }
        RestartingEnvironment restartingEnvironment = new RestartingEnvironment(clientListener.getServer());
        restartingEnvironment.start();
        return new Message(MessageStatus.ACCEPTED).setText("The server is going to stop the work");
    }

    private Message stopServer(@NotNull Message message) {
        if (!MessageStatus.STOP_SERVER.equals(message.getStatus())) {
            String errorMessage = buildMessage("Message of status", MessageStatus.STOP_SERVER
                    , "was expected, but found", message.getStatus());
            LOGGER.warn(errorMessage);
            return new Message(MessageStatus.ERROR).setText("Internal error: ".concat(errorMessage));
        }
        if (!clientListener.getServer().getConfig().getProperty("serverLogin").equals(message.getLogin())
                || !clientListener.getServer().getConfig().getProperty("serverPassword")
                .equals(message.getPassword())) {
            return new Message(MessageStatus.DENIED).setText("Please, check your login and password");
        }
        return new Message(MessageStatus.ACCEPTED).setText("Server is going to shut down");
    }


    /**
     * The method {@code sendMessage} sends an instance of {@code Message} of the {@code MessageStatus.MESSAGE}
     * to the certain {@code Room}
     * It is expected, that {@code message} contains (at least) set following parameters :
     * {@code fromId}
     * {@code roomId}
     * {@code text}
     *
     * @param message a {@code Message} to be sent
     * @return an instance of {@code Message} containing information about the operation execution
     * it may be of {@code MessageStatus.ERROR} either {@code MessageStatus.ACCEPTED}
     * or {@code MessageStatus.DENIED} status
     */
    private Message sendMessage(Message message) {
        if (message == null) {
            LOGGER.error("Message is null");
            return new Message(MessageStatus.ERROR).setText("Internal error. Message is null");
        }
        if (isMessageNotFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Please, log in first");
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
            RoomProcessing.sendMessage(clientListener.getServer(), message);
            responseMessage = new Message(MessageStatus.ACCEPTED);
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            responseMessage = new Message(MessageStatus.ERROR).setText("An internal error occurred");
        } catch (RoomNotFoundException e) {
            LOGGER.trace(buildMessage("Room id", message.getRoomId(), "has not been found"));
            responseMessage = new Message(MessageStatus.ERROR)
                    .setText(buildMessage("Unable to find the room (id", message.getRoomId(), ')'));
        }
        return responseMessage;
    }

    private Message registration(Message message) {
        if (message == null) {
            return new Message(MessageStatus.ERROR).setText("Message came as null");
        }
        if (!MessageStatus.REGISTRATION.equals(message.getStatus())) {
            return new Message(MessageStatus.ERROR).setText(buildMessage("Message of the",
                    MessageStatus.REGISTRATION, "was expected but found", message.getStatus()));
        }
        File clientsDir = new File(clientListener.getServer().getConfig().getProperty("clientsDir"));
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
            client.setServer(clientListener.getServer());
            client.setPassword(password);
            client.setClientId(login.hashCode());
            client.getRooms().safe().add(0);
            if (!clientListener.getServer().getOnlineClients().safe().containsKey(0)) {
                RoomProcessing.loadRoom(clientListener.getServer(), 0);
            }
            Room commomChat = clientListener.getServer().getOnlineRooms().safe().get(0);
            commomChat.getMembers().safe().add(client.getClientId());
            commomChat.save();
        } catch (NullPointerException e) {
            return new Message(MessageStatus.ERROR)
                    .setText("Check whether you have specified all the necessary parameters");
        } catch (InvalidPropertiesFormatException e) {
            LOGGER.error("Wrong properties");
            throw new RuntimeException(e);
        }
        if (!clientListener.getServer().getOnlineRooms().safe().containsKey(0)) {
            try {
                RoomProcessing.loadRoom(clientListener.getServer(), 0);
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
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
        return new Message(MessageStatus.ACCEPTED)
                .setText(buildMessage("The account", login, "has been successfully created"));
    }

    /**
     * The method {@code createRoom} handles with an input request representing by {@code Message}
     * having {@code MessageStatus.CREATE_ROOM} status
     *
     * @param message a command that contains the {@code clientId} of a creator
     * @return an instance of {@code Message} that informs whether new room was created or not
     */
    private Message createRoom(@NotNull Message message) {
        if (isMessageNotFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Please, log in first");
        }
        if (!MessageStatus.CREATE_ROOM.equals(message.getStatus())) {
            LOGGER.error(buildMessage("Unexpected message status. Expected"
                    , MessageStatus.CREATE_ROOM, ". But found", message.getStatus()));
            return new Message(MessageStatus.ERROR)
                    .setText(buildMessage("The message status must be "
                            , MessageStatus.CREATE_ROOM, " but found ", message.getStatus()));
        }
        /*
         *   The field toId is considered as an id of the initial room member, thus it must be valid
         * i.e. the client with such id must exists
         * */
        try {
            Room room = RoomProcessing.createRoom(clientListener.getServer(), message.getFromId());
            if (room == null) {
                return new Message(MessageStatus.ERROR).setText("Some error has occurred during the room creation");
            } else {
                clientListener.getClient().getRooms().safe().add(room.getRoomId());
                LOGGER.trace(new StringBuilder("New room (id").append(room.getRoomId()).append(") has been created"));
                return new Message(MessageStatus.ACCEPTED).setRoomId(room.getRoomId())
                        .setText(buildMessage("The room (id"
                                , room.getRoomId(), ") has been successfully created"));
            }
        } catch (InvalidPropertiesFormatException e) { // error while room creation
            LOGGER.error(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText("Internal has error occurred");
        }
    }

    /**
     * The method {@code userUnban} handles with requests of unblocking a user.
     *
     * @param message an instance of {@code Message} that represents a request about blocking a user.
     *                NOTE! It is expected that message contains following non-null fields
     *                1) {@code fromId} - id of registered user who has admin rights
     *                i.e. an instance of {@code Client} representing his account
     *                has {@code isAdmin == true}
     *                2)  {@code toId} - id of registered client who is currently banned
     * @return an instance of {@code Message} that contains info about performed (or not) operation.
     * It may be of the following statuses:
     * {@code MessageStatus.ACCEPTED}  -   if the specified client has been unbanned
     * {@code MessageStatus.DENIED}    -   if the sender is not an admin or specified client
     * is not currently banned
     * {@code MessageStatus.ERROR}     -   if an error occurred while executing the operation
     */
    private Message clientUnban(Message message) {
        String errorMessage;
        if (message == null) {
            if (LOGGER.isEnabledFor(Level.ERROR)) {
                LOGGER.error("Passed null-message to perform client unbanning");
            }
            return new Message(MessageStatus.ERROR).setText("Error occurred while unbanning (null message)");
        }
        if (message.getToId() == null) {
            errorMessage = "Attempt to unban unspecified account";
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(buildMessage(errorMessage, "from"
                        , message.getFromId() != null ? message.getFromId() : "unspecified client"));
            }
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        int toId = message.getToId();
        if (isMessageNotFromThisLoggedClient(message) && !(
                clientListener.getServer().getConfig().getProperty("serverLogin").equals(message.getLogin())
                        && clientListener.getServer().getConfig().getProperty("serverPassword")
                        .equals(message.getPassword())
        )) {
            errorMessage = buildMessage("Attempt to perform an action before log-in :", message);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        Integer fromId = message.getFromId();
        if (ClientProcessing.hasNotAccountBeenRegistered(clientListener.getServer().getConfig(), toId)) {
            errorMessage = buildMessage("Attempt to unban unregistered client from client (admin) (id"
                    , fromId == null ? "server admin" : fromId);
            if (LOGGER.isEnabledFor(Level.ERROR)) {
                LOGGER.error(errorMessage);
            }
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        boolean isAdmin = true;
        if (fromId != null) {
            isAdmin = loadClient(clientListener.getServer().getConfig(), fromId).isAdmin();
        }
        Client clientToUnban = loadClient(clientListener.getServer().getConfig(), toId);
        clientToUnban.setServer(clientListener.getServer());
        if (!isAdmin) {
            errorMessage = buildMessage("Not enough rights to perform this operation (client id"
                    , fromId, "attempts to unban client id", clientToUnban.getClientId());
            if (LOGGER.isEnabledFor(Level.ERROR)) {
                LOGGER.error(errorMessage);
            }
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (!clientToUnban.isBaned()) {
            errorMessage = buildMessage("Client", "(id", clientToUnban.getClientId(), ')', "is not banned");
            LOGGER.error(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        clientToUnban.setBaned(false);
        clientToUnban.setIsBannedUntil(null);
        if (clientToUnban.save()) {
            String infoMessage = buildMessage("Client (id", clientToUnban.getClientId()
                    , ") has been unbanned by the admin (id ", (fromId == null ? "server admin" : fromId), ')');
            if (LOGGER.isEnabledFor(Level.INFO)) {
                LOGGER.info(infoMessage);
            }
            return new Message(MessageStatus.ACCEPTED).setText(infoMessage);
        } else {
            if (LOGGER.isEnabledFor(Level.WARN)) {
                LOGGER.warn(buildMessage("The process of unbanning client (id", toId
                        , ") has not been finished properly"));
            }
            return new Message(MessageStatus.ERROR).setText("Unknown error occurred while unbanning");
        }
    }

    /**
     * This method handles with the request for the list of the client rooms
     *
     * @param message is the request message
     *                NOTE! It is expected that message contains following non-null fields
     *                1) {@code fromId} - an id of registered user who has logged in
     *                2) {@code roomId} - an id of the room where the client is a member
     *                <p>
     *                NOTE! This method sends the message history by parts - message by message. The contract of the
     *                method is that the caller will send the resulting message of status
     *                {@code MessageStatus.ACCEPTED} to the client i.e.
     *                when the caller obtain success confirmation that means that client has already
     *                received the history
     * @return an instance of {@code Message} that contains info about performed (or not) operation.
     * It may be of the following statuses
     * {@code MessageStatus.ACCEPTED}  -   if the history has been sent
     * {@code MessageStatus.DENIED}    -   if the reques has been obtained from unlogged user
     * {@code MessageStatus.ERROR}     -   if an error occurred while executing the operation
     */
    private synchronized Message getRoomMessages(Message message) {
        if (message == null) {
            return new Message(MessageStatus.ERROR).setText("Internal error occurred. Message is null");
        }
        if (isMessageNotFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Log in first");
        }
        if (message.getRoomId() == null) {
            return new Message(MessageStatus.ERROR).setText("Unspecified room");
        }
        Room room;
        if (!clientListener.getServer().getOnlineRooms().safe().containsKey(message.getRoomId())) {
            if (RoomProcessing.hasRoomBeenCreated(clientListener.getServer().getConfig(), message.getRoomId()) != 0L) {
                try {
                    RoomProcessing.loadRoom(clientListener.getServer(), message.getRoomId());
                } catch (InvalidPropertiesFormatException | RoomNotFoundException e) {
                    return new Message(MessageStatus.ERROR).setText(e.getLocalizedMessage());
                }
            }
        }
        room = clientListener.getServer().getOnlineRooms().safe().get(message.getRoomId());
        if (!RoomProcessing.isMember(clientListener.getServer().getConfig(), clientListener.getClient().getClientId()
                , message.getRoomId())) {
            return new Message(MessageStatus.DENIED).setText(
                    buildMessage("You are not a member of the room (id", message.getRoomId(), ')'));
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            StringWriter stringWriter;
            synchronized (room.getMessageHistory().getMessageHistory()) {
                for (Message roomMessage : room.getMessageHistory().getMessageHistory()) {
                    stringWriter = new StringWriter();
                    marshaller.marshal(roomMessage, stringWriter);
                    clientListener.getOut().safe().writeUTF(stringWriter.toString());
                    clientListener.getOut().safe().flush();
                }
            }
        } catch (JAXBException | IOException e) {
            if (LOGGER.isEnabledFor(Level.ERROR)) {
                LOGGER.error(e.getLocalizedMessage());
            }
        }
        return new Message(MessageStatus.ACCEPTED).setText("This is the end of the room message history")
                .setRoomId(message.getRoomId());
    }

    private synchronized Message getRooms() {
        if (clientListener.getClient().getRooms().safe().size() == 0) {
            return new Message(MessageStatus.ROOM_LIST).setText("");
        }
        StringBuilder stringBuilder = new StringBuilder();
        synchronized (clientListener.getClient().getRooms().safe()) {
            for (int roomId : clientListener.getClient().getRooms().safe()) {
                stringBuilder.append(roomId).append(',');
            }
        }
        return new Message(MessageStatus.ROOM_LIST).setText(stringBuilder.substring(0, stringBuilder.length() - 1));
    }

    private Message addClientToRoom(@NotNull Message message) {
        if (message.getFromId() == null) {
            if (LOGGER.isEnabledFor(Level.WARN)) {
                LOGGER.warn("null fromId");
            }
            return new Message(MessageStatus.ERROR).setText("Missed addresser id");
        }
        if (message.getToId() == null) {
            if (LOGGER.isEnabledFor(Level.WARN)) {
                LOGGER.warn("null toId");
            }
            return new Message(MessageStatus.ERROR).setText("Missed client to be invited");
        }
        if (message.getRoomId() == null) {
            if (LOGGER.isEnabledFor(Level.WARN)) {
                LOGGER.warn("null roomId");
            }
            return new Message(MessageStatus.ERROR).setText("Missed roomId");
        }
        if (!clientListener.getServer().getOnlineRooms().safe().containsKey(message.getRoomId())) {
            String errorMessage;
            try {
                RoomProcessing.loadRoom(clientListener.getServer(), message.getRoomId());
            } catch (InvalidPropertiesFormatException e) {
                if (LOGGER.isEnabledFor(Level.ERROR)) {
                    LOGGER.error(buildMessage("Unknown error", e.getClass().getName(), ' ', e.getMessage()));
                }
                return new Message(MessageStatus.ERROR).setText("Internal error");
            } catch (RoomNotFoundException e) {
                errorMessage = buildMessage("Unable to find a room (id", message.getRoomId(), ')');
                if (LOGGER.isEnabledFor(Level.TRACE)) {
                    LOGGER.trace(errorMessage);
                }
                return new Message(MessageStatus.DENIED).setText(buildMessage(errorMessage));
            }
        }
        Room room = clientListener.getServer().getOnlineRooms().safe().get(message.getRoomId());
        if (!room.getMembers().safe().contains(message.getFromId())) {
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(buildMessage("The client id", message.getFromId()
                        , "is not a member of the room id", message.getRoomId()));
            }
            return new Message(MessageStatus.DENIED).setText("Not a member of the room");
        }
        if (room.getMembers().safe().contains(message.getToId())) {
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(buildMessage("Attempt to remove client (id", message.getToId()
                        , ") who is already a member of the room (id", message.getRoomId(), ')'));
            }
            return new Message(MessageStatus.DENIED).setText("This client is already a member of the room");
        }
        room.getMembers().safe().add(message.getToId());
        String infoString = buildMessage("Client (id", message.getToId()
                , ") now is a member of the room (id", message.getRoomId(), ')');
        Client client;
        if (clientListener.getServer().getOnlineClients().safe().containsKey(message.getToId())) {
            client = clientListener.getServer().getOnlineClients().safe().get(message.getToId()).getClient();
            clientListener.getServer().getOnlineClients().safe().get(message.getToId()).sendMessageToConnectedClient(
                    new Message(MessageStatus.UNINVITE_USER).setText("You have been invited to the room")
                            .setRoomId(message.getRoomId()));
        } else {
            client = ClientProcessing.loadClient(clientListener.getServer().getConfig(), message.getToId());
        }
        client.getRooms().safe().add(message.getRoomId());
        if (LOGGER.isEnabledFor(Level.TRACE)) {
            LOGGER.trace(infoString);
        }
        return new Message(MessageStatus.ACCEPTED).setText(infoString);
    }

    private Message kickClientFromRoom(@NotNull Message message) {
        if (message.getFromId() == null) {
            if (LOGGER.isEnabledFor(Level.WARN)) {
                LOGGER.warn("null fromId");
            }
            return new Message(MessageStatus.ERROR).setText("Missed addresser id");
        }
        if (message.getToId() == null) {
            if (LOGGER.isEnabledFor(Level.WARN)) {
                LOGGER.warn("null toId");
            }
            return new Message(MessageStatus.ERROR).setText("Missed client to be uninvited");
        }
        if (message.getRoomId() == null) {
            if (LOGGER.isEnabledFor(Level.WARN)) {
                LOGGER.warn("null roomId");
            }
            return new Message(MessageStatus.ERROR).setText("Missed roomId");
        }
        if (!clientListener.getServer().getOnlineRooms().safe().containsKey(message.getRoomId())) {
            String errorMessage;
            try {
                RoomProcessing.loadRoom(clientListener.getServer(), message.getRoomId());
            } catch (InvalidPropertiesFormatException e) {
                if (LOGGER.isEnabledFor(Level.ERROR)) {
                    LOGGER.error(buildMessage("Unknown error", e.getClass().getName(), e.getMessage()));
                }
                return new Message(MessageStatus.ERROR).setText("Internal error");
            } catch (RoomNotFoundException e) {
                errorMessage = buildMessage("Unable to find a room");
                if (LOGGER.isEnabledFor(Level.TRACE)) {
                    LOGGER.trace(buildMessage(errorMessage, "(id", message.getRoomId(), ')'));
                }
                return new Message(MessageStatus.DENIED).setText(errorMessage).setRoomId(message.getRoomId());
            }
        }
        Room room = clientListener.getServer().getOnlineRooms().safe().get(message.getRoomId());
        if (!room.getMembers().safe().contains(message.getFromId())) {
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(buildMessage("The client (id", message.getFromId()
                        , ") is not a member of the room id", message.getRoomId()));
            }
            return new Message(MessageStatus.DENIED).setText("Not a member of the room");
        }
        if (!room.getMembers().safe().contains(message.getToId())) {
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(buildMessage("Attempt to remove client (id", message.getToId()
                        , ") who is not a member of the room (id", message.getRoomId(), ')'));
            }
            return new Message(MessageStatus.DENIED).setText("This client is not a member of the room");
        }
        room.getMembers().safe().remove(message.getToId());
        Client client;
        if (clientListener.getServer().getOnlineClients().safe().containsKey(message.getToId())) {
            client = clientListener.getServer().getOnlineClients().safe().get(message.getToId()).getClient();
            clientListener.getServer().getOnlineClients().safe().get(message.getToId()).sendMessageToConnectedClient(
                    new Message(MessageStatus.UNINVITE_USER).setText("You have been uninvited from the room")
                            .setRoomId(message.getRoomId()));
        } else {
            client = ClientProcessing.loadClient(clientListener.getServer().getConfig(), message.getToId());
        }
        client.getRooms().safe().remove(message.getRoomId());
        client.save();
        String infoString = buildMessage("Now client (id", message.getToId()
                , ") is not a member of the room (id", message.getRoomId(), ')');
        if (LOGGER.isEnabledFor(Level.TRACE)) {
            LOGGER.trace(infoString);
        }
        return new Message(MessageStatus.ACCEPTED).setText(infoString);
    }

    private Message getClientName(Message message) {
        if (isMessageNotFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED)
                    .setText("Log in prior to request information");
        }
        if (message.getToId() == null) {
            return new Message(MessageStatus.ERROR).setText("Unspecified client id");
        }
        int clientId = message.getToId();
        if (ClientProcessing.hasNotAccountBeenRegistered(clientListener.getServer().getConfig(), clientId)) {
            return new Message(MessageStatus.DENIED)
                    .setText(buildMessage("Unable to find client id", clientId));
        }
        Client client;
        if (clientListener.getServer().getOnlineClients().safe().containsKey(clientId)) {
            client = clientListener.getServer().getOnlineClients().safe().get(clientId).getClient();
        } else {
            client = ClientProcessing.loadClient(clientListener.getServer().getConfig(), clientId);
        }
        return new Message(MessageStatus.ACCEPTED).setFromId(clientId).setText(client.getLogin());
    }

    /**
     *  The method returns a string that contains enumerated clients ids of the {@code Room}
     * specified by the {@code roomId} in passed {@code message}.
     *
     * @param           message a message that contains following information:
     *                          1) {@code fromId} the {@code clientId} of the client who requests members
     *                          2) {@code roomId} the corresponding parameter of the room
     *
     * @return          an instance of {@code Message} that contains information about room members
     *                  (of {@code MessageStatus.ACCEPTED} or error message of {@code MessageStatus.ERROR}
     *                  or {@code MessageStatus.DENIED}). The message of status {@code MessageStatus.ACCEPTED}
     *                  contains ids of the clients enumerated in the {@code text} field
     *                  of the message separated by comas.
     * */
    private Message getRoomMembers(Message message) {
        if (isMessageNotFromThisLoggedClient(message)) {
            return new Message(MessageStatus.DENIED).setText("Log in prior to request information");
        }
        if (message.getRoomId() == null) {
            return new Message(MessageStatus.ERROR).setText("Unspecified roomId");
        }
        int roomId = message.getRoomId();
        if (RoomProcessing.hasRoomBeenCreated(clientListener.getServer().getConfig(), roomId) == 0) {
            return new Message(MessageStatus.ERROR)
                    .setText(buildMessage("Unable to find the room (id", roomId, ')'));
        }
        Room room;
        if (!clientListener.getServer().getOnlineRooms().safe().containsKey(roomId)) {
            try {
                RoomProcessing.loadRoom(clientListener.getServer(), roomId);
            } catch (InvalidPropertiesFormatException e) {
                if (LOGGER.isEnabledFor(Level.ERROR)) {
                    LOGGER.error(buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
                }
                return new Message(MessageStatus.ERROR).setText("Internal error occurred");
            }
        }
        room = clientListener.getServer().getOnlineRooms().safe().get(roomId);
        StringBuilder stringBuilder = new StringBuilder();
        synchronized (room.getMembers().safe()) {
            for (int clientId : room.getMembers().safe()) {
                stringBuilder.append(clientId).append(",");
            }
        }
        return new Message(MessageStatus.ACCEPTED)
                .setText(stringBuilder.substring(0, stringBuilder.length() - 1)).setRoomId(roomId);
    }
}