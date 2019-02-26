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
import java.time.LocalDateTime;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

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
public class ClientListener extends Thread{
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
        connected = LocalDateTime.now();
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    @Override
    public void run() {
        try {
            while (!logged) {
                ++connectAttempts;
                Message firstMessage = Message.from(in.readUTF());
                if(!MessageStatus.AUTH.equals(firstMessage.getStatus())
                        && !MessageStatus.REGISTRATION.equals(firstMessage.getStatus())) {
                    if(connectAttempts == 3) {
                        sendResponseMessage(new Message(MessageStatus.ERROR)
                                .setText("Too much wrong input, please, try again later"));
                        closeClientSession();
                    }
                    Message wrongFirstMessage = new Message(MessageStatus.ERROR)
                            .setText(new StringBuilder("The first message must be either ")
                                    .append(MessageStatus.AUTH).append(" or ").append(MessageStatus.REGISTRATION)
                                    .append(" status. But found ").append(firstMessage.getStatus()).toString());
                    sendResponseMessage(wrongFirstMessage);
                }
                try {
                    Message responseMessage;
                    if (MessageStatus.AUTH.equals(firstMessage.getStatus())) {
                        responseMessage = auth(firstMessage);
                    } else {
                        responseMessage = registration(firstMessage);
                    }
                    sendResponseMessage(responseMessage);
                } catch (IllegalPasswordException | ClientNotFoundException e) {
                    LOGGER.warn(e.getLocalizedMessage());
                    sendResponseMessage(new Message(MessageStatus.DENIED)
                            .setText("Login or password is incorrect. Please, check your data"));
                }
            }
        } catch (SocketException e) {
            if (logged) {
                client.save();
            }
            interrupt();
            return;
        } catch(IOException e) {
            LOGGER.fatal(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
        while (true) {
            try {
                handle(Message.from(in.readUTF()));
                lastInputMessage = LocalDateTime.now();
            } catch (SocketException e) {
                if (logged) {
                    client.save();
                }
                break;
            } catch (Exception e) {
                try {
                    sendResponseMessage(new Message(MessageStatus.ERROR)
                            .setText(e.getClass().getName().concat(" occurred while handling the message")));
                } catch (IOException e1) {
                    LOGGER.fatal(e1.getLocalizedMessage());
                    break;
                }
            }
        }
        interrupt();
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

    private void handle(Message message) {
        if(message == null){
            throw new NullPointerException("Message must not be null");
        }
        Message responseMessage = null;
        switch (message.getStatus()) {
            case AUTH:
                responseMessage = auth(message);
                break;
            case REGISTRATION:
                responseMessage = registration(message);
                break;
            case MESSAGE:
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
                responseMessage = createRoom(message);
                break;
            case DELETE_ROOM:
                break;
            case INVITE_USER:
                break;
            case UNINVITE_USER:
                break;
                default: throw new RuntimeException(new StringBuilder("Unknown message status")
                        .append(message.getStatus().toString()).toString());
        }
        try {
            sendResponseMessage(responseMessage);
            if (MessageStatus.REGISTRATION.equals(message.getStatus())) {
                sendResponseMessage(new Message(MessageStatus.KICK).setText("Please, re-login on the server"));
                closeClientSession();
            }
        } catch (IOException e) {
            LOGGER.fatal(e.getLocalizedMessage());
            if (logged) {
                client.save();
            }
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
            LOGGER.error(new StringBuilder("Message of the ").append(MessageStatus.AUTH)
                    .append(" was expected but found ").append(message.getStatus().toString()));
            return new Message(MessageStatus.ERROR).setText("Internal error");
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
                this.client = Client.from(clientFile);
                this.client.setServer(server);
                return new Message(MessageStatus.ACCEPTED);
            } else {
                return new Message(MessageStatus.DENIED).setText("Please, check your password and login");
            }
        } catch (JAXBException e) {
            LOGGER.fatal(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText("Internal error");
        } catch (FileNotFoundException e) {
            LOGGER.error("Unable to find client's file ".concat(clientFile.getAbsolutePath()));
            return new Message(MessageStatus.ERROR).setText("Internal error. Unable to find client's file");
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
        client.save();
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

    public void sendResponseMessage(Message message) throws IOException {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter stringWriter = new StringWriter();
            marshaller.marshal(message, stringWriter);
            out.writeUTF(stringWriter.toString());
            out.flush();
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
    }

    public void closeClientSession() throws IOException {
        if(isAlive() && !isInterrupted()){
            in.close();
            out.close();
            socket.close();
            if (logged) {
                client.save();
                server.getOnlineClients().remove(client.getClientId());
            }
            interrupt();
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

    private void userBan(@NotNull Message message) {
        if (ServerProcessing.hasAccountBeenRegistered(server.getConfig(), message.getToId())) {
            throw new ClientNotFoundException(new StringBuilder("Unable to find a client id ")
                    .append(message.getToId()).toString());
        }
        Client target = loadClient(server.getConfig(), message.getToId());
        if (!target.isBaned()) {
            LOGGER.info(new StringBuilder("The client id ").append(message.getToId())
                    .append(" is not banned").toString());
            throw new IllegalStateException(new StringBuilder("The client id ").append(message.getToId())
                    .append(" is not baned").toString());
        }
        if (!client.isAdmin()) {
            LOGGER.info("Not enough rights to perform ban operation");
            throw new IllegalOperationException("Not enough rights to perform ban operation");
        }
        target.setIsBannedUntill(null);
        target.setBaned(false);
    }

    private void userUnBan(@NotNull Message message) {
        Client target = loadClient(server.getConfig(), message.getToId());
        if (target.isBaned()) {
            LOGGER.info(new StringBuilder("The client id ").append(message.getToId())
                    .append(" is already baned").toString());
            throw new IllegalStateException(new StringBuilder("The client id ").append(message.getToId())
                    .append(" is already baned").toString());
        }
        if (!client.isAdmin()) {
            LOGGER.info("Not enough rights to perform ban operation");
            throw new IllegalOperationException("Not enough rights to perform ban operation");
        }
        if (target.isAdmin()) {
            throw new IllegalOperationException("Not enough rights to ban the admin");
        }
        LocalDateTime willBebannedUntill = LocalDateTime.parse(message.getText(), Server.dateTimeFormatter);
        if (willBebannedUntill.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("The future date is required, but found "
                    .concat(willBebannedUntill.toString()));
        }
        target.setIsBannedUntill(willBebannedUntill);
        target.setBaned(true);
    }

    private void leaveRoom(@NotNull Message message) {
        int roomId = message.getRoomId();
        int fromId = message.getFromId();
        if (RoomProcessing.hasRoomBeenCreated(server.getConfig(), roomId) == 0L) {
            throw new RoomNotFoundException("Unable to find the room id ".concat(String.valueOf(roomId)));
        }
        if (!ClientListener.clientExists(server.getConfig(), fromId)) {
            throw new ClientNotFoundException("Unable to find the client id ".concat(String.valueOf(fromId)));
        }
        Room room;
        if (!server.getOnlineRooms().containsKey(roomId)) {
            server.loadRoomToOnlineRooms(roomId);
        }
        room = server.getOnlineRooms().get(roomId);
        if (room.getMembers().contains(fromId)) {
            room.getMembers().remove(fromId);
        } else {
            throw new ClientNotFoundException(new StringBuilder("The client id ").append(fromId)
                    .append(" is not a member of a room id ").append(roomId).toString());
        }
    }

    private static Client loadClient(Properties serverProperties, int clientId) {
        if (!clientExists(serverProperties, clientId)) {
            throw new ClientNotFoundException("Unable to find client id ".concat(String.valueOf(clientId)));
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
}