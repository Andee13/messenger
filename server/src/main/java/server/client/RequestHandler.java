package server.client;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import server.exceptions.ClientNotFoundException;
import server.exceptions.RoomNotFoundException;
import server.handlers.AuthorizationRequestHandler;
import server.handlers.ClientBanRequestHandler;
import server.handlers.MessageSendingRequestHandler;
import server.handlers.RegistrationRequestHandler;
import server.processing.ClientProcessing;
import server.processing.RestartingEnvironment;
import server.processing.ServerProcessing;
import server.room.Room;
import server.room.RoomProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
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
public
class RequestHandler {
    private ClientListener clientListener;
    public static Logger LOGGER = Logger.getLogger(RequestHandler.class.getSimpleName());

    void handle(Message message) {
        server.handlers.RequestHandler rh;
        Message responseMessage = new Message(MessageStatus.ERROR)
                .setText("This is a default text. If you got this message, that means that something went wrong.");
        try {
            switch (message.getStatus()) {
                case AUTH:
                    rh = new AuthorizationRequestHandler(clientListener, message);
                    responseMessage = rh.handle();
                    break;
                case REGISTRATION:
                    rh = new RegistrationRequestHandler(clientListener, message);
                    responseMessage = rh.handle();
                    break;
                case MESSAGE:
                    rh = new MessageSendingRequestHandler(clientListener, message);
                    responseMessage = rh.handle();
                    break;
                case CLIENTBAN:
                    rh = new ClientBanRequestHandler(clientListener, message);
                    responseMessage = rh.handle();
                    break;
                case CREATE_ROOM:
                    if (clientListener.isMessageNotFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    responseMessage = createRoom(message);
                    break;
                case DELETE_ROOM:
                    if (clientListener.isMessageNotFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    break;
                case INVITE_USER:
                    if (clientListener.isMessageNotFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    } else {
                        responseMessage = addClientToRoom(message);
                    }
                    break;
                case UNINVITE_USER:
                    if (clientListener.isMessageNotFromThisLoggedClient(message)) {
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

    private Message restartServer(Message message) {
        if ((clientListener.isMessageNotFromThisLoggedClient(message))
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
     * The method {@code createRoom} handles with an input request representing by {@code Message}
     * having {@code MessageStatus.CREATE_ROOM} status
     *
     * @param message a command that contains the {@code clientId} of a creator
     * @return an instance of {@code Message} that informs whether new room was created or not
     */
    private Message createRoom(@NotNull Message message) {
        if (clientListener.isMessageNotFromThisLoggedClient(message)) {
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
        if (clientListener.isMessageNotFromThisLoggedClient(message) && !(
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
        if (clientListener.isMessageNotFromThisLoggedClient(message)) {
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
        if (clientListener.isMessageNotFromThisLoggedClient(message)) {
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
        if (clientListener.isMessageNotFromThisLoggedClient(message)) {
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