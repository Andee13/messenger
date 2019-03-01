package server;

import common.message.Message;
import common.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import server.exceptions.ClientNotFoundException;
import server.exceptions.IllegalOperationException;
import server.exceptions.IllegalPasswordException;
import server.room.Room;
import server.room.RoomProcessing;
import sun.misc.Cleaner;

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
import java.util.*;

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
public class ClientListener extends Thread implements Saveable{
    private Socket socket;
    private Server server;
    private DataOutputStream out;
    private DataInputStream in;
    private LocalDateTime lastInputMessage;
    private boolean logged;
    private LocalDateTime connected;
    private Client client;
    private int connectAttempts;

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    private static final Logger LOGGER = Logger.getLogger("Client");

    public ClientListener(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        socket.setSoTimeout(1000 /*ms*/ * 60 /*s*/ * 60 /*m*/);
        connected = LocalDateTime.now();
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    @Override
    public void run() {
        if (server == null) {
            LOGGER.fatal("Server must not be null");
            interrupt();
            return;
        } else if (!State.RUNNABLE.equals(server.getState())) {
            LOGGER.fatal(new StringBuilder("Server must have ")
                    .append(State.RUNNABLE)
                    .append(" state, but currently has ")
                    .append(server.getState()));
            interrupt();
            return;
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            String messageXml;
            try {
                while (!isInterrupted()) {
                    messageXml = in.readUTF();
                    handle((Message) unmarshaller.unmarshal(new StringReader(messageXml)));
                }
            } catch (SocketException e) {
                StringBuilder stringBuilder = new StringBuilder("The client ");
                if (client != null) {
                    stringBuilder.append(" (id ").append(client.getClientId()).append(") ");
                }
                stringBuilder.append(" disconnected (address ").append(socket.getRemoteSocketAddress());
                LOGGER.info(stringBuilder.toString());
                if (!save()) {
                    LOGGER.warn(new StringBuilder("Saving the client id ").append(client.getClientId())
                            .append(" has not been completed properly"));
                }
            } catch (IOException e) {
                LOGGER.trace(e.getLocalizedMessage());
            }
        } catch (JAXBException e) {
            LOGGER.fatal(e.getLocalizedMessage());
        } finally {
            closeClientSessionSafely();
            interrupt();
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public DataOutputStream getOut() {
        return out;
    }

    public DataInputStream getIn() {
        return in;
    }

    public LocalDateTime getLastInputMessage(){
        return LocalDateTime.from(lastInputMessage);
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
            LOGGER.trace("Passed null-message value to check the addresser id");
            return false;
        }
        if (!logged) {
            LOGGER.trace("Passed message to check before log-in: ".concat(message.toString()));
            return false;
        }
        if (logged && (message.getFromId() == null || message.getFromId() != client.getClientId())) {
            LOGGER.warn(new StringBuilder("Expected to receive clientId ").append(client.getClientId())
                    .append(" but found ").append(message.getFromId()));
            return false;
        }
        return true;
    }

    private void handle(Message message) {
        Message responseMessage = null;
        try {
            switch (message.getStatus()) {
                case AUTH:
                    responseMessage = auth(message);
                    break;
                case REGISTRATION:
                    responseMessage = registration(message);
                    break;
                case MESSAGE:
                    if (!isMessageFromThisLoggedClient(message)) {
                        responseMessage = new Message(MessageStatus.DENIED).setText("Wrong passed clientId");
                    }
                    try {
                        RoomProcessing.sendMessage(server, message);
                        responseMessage = new Message(MessageStatus.ACCEPTED);
                    } catch (IOException e) {
                        LOGGER.error(e.getLocalizedMessage());
                        responseMessage = new Message(MessageStatus.ERROR)
                                .setText("Internal error. Message has not been sent");
                    }
                    break;
                case USERBAN:
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
                    break;
                default: throw new RuntimeException(new StringBuilder("Unknown message status")
                        .append(message.getStatus().toString()).toString());
            }
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
            responseMessage = new Message(MessageStatus.ERROR)
                    .setText(new StringBuilder("Internal ").append(e.getClass().getName()).append(" occurred").toString());
        } finally {
            sendMessageToConnectedClient(responseMessage);
            if (MessageStatus.REGISTRATION.equals(message.getStatus())
                    && MessageStatus.ACCEPTED.equals(responseMessage.getStatus())) {
                sendMessageToConnectedClient(new Message(MessageStatus.KICK).setText("Please, re-login on the server"));
                closeClientSessionSafely();
            }
        }
    }

    private Message stopServer(@NotNull Message message) {
        if (!MessageStatus.STOP_SERVER.equals(message.getStatus())) {
            String errorMessage = new StringBuilder("Message of status ").append(MessageStatus.STOP_SERVER)
                    .append(" was expected, but found ").append(message.getStatus()).toString();
            LOGGER.warn(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (!server.getConfig().getProperty("server_login").equals(message.getLogin())
                || !server.getConfig().getProperty("server_password").equals(message.getPassword())) {
            return new Message(MessageStatus.DENIED).setText("Please, check your login and password");
        }
        ServerProcessing.stopServerSafety(server);
        if (server.isInterrupted()) {
            LOGGER.info("The server has been stopped from address ".concat(socket.getInetAddress().toString()));
            return new Message(MessageStatus.ACCEPTED).setText("The server has been stopped");
        } else {
            LOGGER.error("Attempt to stop the server has been failed");
            return new Message(MessageStatus.ERROR).setText("Unable to stop the server");
        }
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
        String text = message.getText();
        if (text == null) {
            LOGGER.info(new StringBuilder("Attempt to send an empty message from client id ")
                    .append(message.getToId()).append(" to the room id ").append(message.getRoomId()));
            return new Message(MessageStatus.ERROR).setText("Message text has not been set");
        }
        if (message.getFromId() == null) {
            LOGGER.info("Attempt to send an anonymous message : fromId is null");
            return new Message(MessageStatus.ERROR).setText("Client's id has not been set");
        }
        int fromId = message.getFromId();
        if (message.getRoomId() == null) {
            LOGGER.info(new StringBuilder("Attempt to sent a message from client id ").append(fromId)
                    .append(" to undefined room"));
        }
        int roomId = message.getRoomId();
        Message responseMessage;
        try {
            RoomProcessing.sendMessage(server, message);
            responseMessage = new Message(MessageStatus.ACCEPTED);
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            responseMessage = new Message(MessageStatus.ERROR).setText("An internal error occurred");
        }
        return responseMessage;
    }

    /**
     * The method that turns an incoming connection to a client's session
     * Verifies the {@code message} of status {@code MessageStatus.AUTH} comparing the incoming user data
     * such as a login and a password.
     *
     * @param           message a message of {@code MessageStatus.AUTH} containing a login and a password
     *
     * @throws          IOException if one or more of the fields {@code fromId}, {@code login} or {@code password}
     *                              are not specified. Also it is thrown in cases when {@code message} is not of
     *                              status {@code MessageStatus.AUTH}
     *
     * @exception       IllegalPasswordException in case if the password from the {@code message}
     *                              does not match the one from the userfile
     *
     * @exception ClientNotFoundException if the specified client's file has not been found
     *                              in the {@code clientsDir} folder or there is not user data file
     *
     * @exception       NullPointerException in case when message equals {@code null}
     *
     * */
    private Message auth(Message message) {
        if (message == null) {
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        if (!MessageStatus.AUTH.equals(message.getStatus())) {
            StringBuilder errorStringBuilder = new StringBuilder("Message of the ").append(MessageStatus.AUTH)
                    .append(" was expected but found ").append(message.getStatus().toString());
            LOGGER.warn(errorStringBuilder.toString());
            return new Message(MessageStatus.ERROR).setText(errorStringBuilder.toString());
        }
        if (message.getLogin() == null || message.getPassword() == null) {
            return new Message(MessageStatus.ERROR)
                    .setText((message.getLogin() == null ? "Login" : "Password").concat(" must be set"));
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
            logged = client.getPassword().equals(message.getPassword());
            if (logged) {
                this.client = client;
                this.client.setServer(server);
                System.out.println(client);
                LOGGER.trace(new StringBuilder("Client id ").append(client.getClientId()).append(" has logged in"));
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
        if(message == null){
            return new Message(MessageStatus.ERROR).setText("Message came as null");
        }
        if (!MessageStatus.REGISTRATION.equals(message.getStatus())) {
            return new Message(MessageStatus.ERROR).setText(new StringBuilder("Message of the ")
                    .append(MessageStatus.REGISTRATION).append(" was expected but found ")
                    .append(message.getStatus()).toString());
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
            return new Message(MessageStatus.DENIED).setText(new StringBuilder("The login ")
                    .append(login).append(" is already taken").toString());
        }
        try {
            if (!clientDir.mkdir() || !clientFile.createNewFile()) {
                throw new IOException();
            }
        } catch (IOException e) {
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        Client client = new Client();
        client.setLogin(login);
        client.setServer(server);
        client.setPassword(password);
        client.setClientId(login.hashCode());
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(client, clientFile);
            LOGGER.info(new StringBuilder("New client id ").append(client.getClientId()).append(" has been registered"));
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
        return new Message(MessageStatus.ACCEPTED).setText(new StringBuilder("The account ")
                .append(login).append(" has been successfully created").toString());

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
        if (!message.getStatus().equals(MessageStatus.CREATE_ROOM)) {
            return new Message(MessageStatus.ERROR)
                    .setText(new StringBuilder("The message status must be ").append(MessageStatus.CREATE_ROOM)
                            .append(" but found ").append(message.getStatus()).toString());
        }
        /*
        * The field toId is considered as an id of the initial room member, thus it must be valid
        * i.e. the client with such id must exists
        * */
        try {
            Room room = RoomProcessing.createRoom(server, message.getFromId());
            if (room == null) {
                return new Message(MessageStatus.ERROR).setText("Some error has occurred during the room creation");
            } else {
                client.getRooms().add(room.getRoomId());
                client.save();
                return new Message(MessageStatus.ACCEPTED).setRoomId(room.getRoomId())
                        .setText(new StringBuilder("The room id: ").append(room.getRoomId())
                                .append(" has been successfully created").toString());
            }
        } catch (InvalidPropertiesFormatException e) {
            LOGGER.error(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText("Internal has error occurred");
        }
    }

    public LocalDateTime getConnected() {
        return LocalDateTime.from(connected);
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
        } catch (SocketException e) {
            LOGGER.warn("The connection was closed");
        } catch (IOException | JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
    }

    /**
     *  The method {@code closeClientSession} closes client's socket and all the streams were used for wrapping the
     * socket's streams (i.e. streams have been got by calling {@code socket.getInputStream()},
     * {@code socket.getOutputStream()})
     *
     *  Also it saves the client's data if the person has logged in and removes current thread from server list of
     * online clients
     *
     * NOTE! This method does not interrupt current stream
     *
     * @return          In case if no user has been set (e.g. before registration) the method returns {@code true}
     *                  if the {@code socket} was closed
     *                  If the {@code client} was set - the method will return {@code true} if and only if the condition
     *                  above are met and the {@code client} has been successfully saved
     * */
    public boolean closeClientSessionSafely() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            in.close();
            out.close();
            Map<Integer, ClientListener> onlineClients = server.getOnlineClients();
            if (client != null) {
                onlineClients.remove(client.getClientId());
                return client.save() & socket.isClosed();
            }
            return socket.isClosed();
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
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

    public Set<Integer> getClientFriendsList () {
        if (client == null) {
            LOGGER.error("Attempt to get a set of friends ids of unspecified client");
            throw new IllegalStateException("Client has not been set");
        }
        return client.getFriends();
    }

    public Set<Integer> getClientRoomsList () {
        if (client == null) {
            LOGGER.error("Attempt to get a set of room ids of unspecified client");
            throw new IllegalStateException("Client has not been set");
        }
        return client.getRooms();
    }

    /**
     *  Just invokes the same method of the {@code client}
     *
     * @return          {@code true} if and only if the {@code client} is set and it has been successfully saved
     * */
    @Override
    public boolean save() {
        return client != null && client.save();
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
        int toId = message.getToId();
        if (!isMessageFromThisLoggedClient(message)) {
            errorMessageBuilder = new StringBuilder("Attempt to perform an action before log-in");
            errorMessage = errorMessageBuilder.toString();
            LOGGER.trace(errorMessageBuilder.append(": ").append(message).toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        int fromId = message.getFromId();
        if (message.getText() == null) {
            errorMessageBuilder = new StringBuilder("Attempt to ban client without specifying the term");
            errorMessage = errorMessageBuilder.toString();
            LOGGER.trace(errorMessageBuilder
                    .append(" from ").append(message.getFromId()).append(" to ").append(message.getToId()).toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        LocalDateTime bannedUntil/* = null*/;
        try {
            bannedUntil = LocalDateTime.parse(message.getText(), ServerProcessing.DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            errorMessageBuilder = new StringBuilder("Invalid (unparseable) end data of ban has been set: ")
                    .append(message.getText());
            errorMessage = errorMessageBuilder.toString();
            errorMessageBuilder.append(" from ").append(fromId).append(" to ").append(toId);
            LOGGER.trace(errorMessageBuilder.toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (LocalDateTime.now().isAfter(bannedUntil)) {
            errorMessageBuilder = new StringBuilder("Invalid (past) end data of ban has been set: ").append(bannedUntil);
            errorMessage = errorMessageBuilder.toString();
            errorMessageBuilder.append(" from ").append(fromId).append(" to ").append(toId);
            LOGGER.trace(errorMessageBuilder.toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        Client clientIsBeingBanned;
        try {
            clientIsBeingBanned = loadClient(server.getConfig(), toId);
        } catch (ClientNotFoundException e) {
            errorMessageBuilder = new StringBuilder("Client id ").append(e.getClientId()).append(" has not been found");
            LOGGER.error(errorMessageBuilder.toString());
            return new Message(MessageStatus.ERROR).setText(errorMessageBuilder.toString());
        }
        Client admin = server.getOnlineClients().get(fromId).getClient();
        boolean isAdmin = admin.isAdmin();
        boolean isAlreadyBanned = clientIsBeingBanned.isBaned();
        boolean isBeingBannedAdmin = clientIsBeingBanned.isAdmin();
        if (!isAdmin || isBeingBannedAdmin || isAlreadyBanned || bannedUntil.isBefore(LocalDateTime.now())) {
            String deniedMessage = "Not enough rights to perform this operation: ".concat(
                    (!isAdmin || isBeingBannedAdmin) ?  "not enough rights" :
                    (isAlreadyBanned) ? "the specified client is already banned" : "invalid date");
            LOGGER.trace(deniedMessage);
            return new Message(MessageStatus.DENIED).setText(deniedMessage);
        }
        server.closeClientSession(toId);
        clientIsBeingBanned.setBaned(true);
        clientIsBeingBanned.setIsBannedUntill(bannedUntil);
        clientIsBeingBanned.save();
        return new Message(MessageStatus.ACCEPTED).setText(new StringBuilder("The client id ").append(toId)
                .append(" has been banned").toString());
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
        StringBuilder errorMessageBuilder;
        if (message == null) {
            LOGGER.error("Passed null-message to perform client unbanning");
            return new Message(MessageStatus.ERROR).setText("Error occurred while unbanning (null)");
        }
        if (message.getToId() == null) {
            errorMessageBuilder = new StringBuilder("Attempt to unban unspecified account");
            errorMessage = errorMessageBuilder.toString();
            LOGGER.trace(errorMessageBuilder.append(" from ")
                    .append(message.getFromId() != null ? message.getFromId() : "unspecified client").toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        int toId = message.getToId();
        if (!isMessageFromThisLoggedClient(message)) {
            errorMessageBuilder = new StringBuilder("Attempt to perform an action before log-in");
            errorMessage = errorMessageBuilder.append(": ").toString();
            LOGGER.trace(errorMessageBuilder.append(": ").append(message).toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        int fromId = message.getFromId();
        if (ServerProcessing.hasAccountBeenRegistered(server.getConfig(), toId)) {
            errorMessageBuilder = new StringBuilder("Attempt to unban unregistered client");
            errorMessage = errorMessageBuilder.toString();
            LOGGER.error(errorMessageBuilder.append(" from client (admin) id ").append(fromId).toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        Client admin = loadClient(server.getConfig(), fromId);
        Client clientToUnban = loadClient(server.getConfig(), toId);
        if (!admin.isAdmin()) {
            errorMessageBuilder = new StringBuilder("Not enough rights to perform this operation");
            errorMessage = errorMessageBuilder.toString();
            LOGGER.error(errorMessageBuilder.append(" (client id ").append(admin.getClientId())
                    .append(" attempts to unban client id ").append(clientToUnban.getClientId()).toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (!clientToUnban.isBaned()) {
            errorMessageBuilder = new StringBuilder("Client is not banned");
            errorMessage = errorMessageBuilder.toString();
            LOGGER.error(errorMessageBuilder.append("(id ").append(clientToUnban.getClientId()).append(")").toString());
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        clientToUnban.setBaned(false);
        clientToUnban.setIsBannedUntill(null);
        clientToUnban.save();
        return new Message(MessageStatus.ACCEPTED)
                .setText(new StringBuilder("Client id ").append(toId).append(" is unbanned").toString());
    }
}