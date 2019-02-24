package server;

import org.jetbrains.annotations.Contract;
import server.exceptions.IllegalOperationException;
import server.exceptions.RoomNotFoundException;
import server.room.Room;
import server.room.RoomProcessing;
import common.message.Message;
import common.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.xpath.*;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import server.exceptions.IllegalPasswordException;
import server.exceptions.ClientNotFoundException;

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

    public ClientListener(){
    }

    private static final Logger LOGGER = Logger.getLogger("ClientListener");

    public ClientListener(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        connected = LocalDateTime.now();
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    @Override
    public void run() {
        JAXBContext jaxbContext;
        try {
            jaxbContext = JAXBContext.newInstance(Message.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            while (!logged) {
                ++connectAttempts;
                Message firstMessage = (Message) unmarshaller.unmarshal(new InputSource(new StringReader(in.readUTF())));
                if(!MessageStatus.AUTH.equals(firstMessage.getStatus())
                        && !MessageStatus.REGISTRATION.equals(firstMessage.getStatus())) {
                    if(connectAttempts == 3) {
                        closeClientSession();
                    }
                    Message wrongFirstMessage = new Message(MessageStatus.ERROR)
                            .setText(new StringBuilder("The first message must be either ")
                                    .append(MessageStatus.AUTH).append(" or ").append(MessageStatus.REGISTRATION)
                                    .append(" status. But found ").append(firstMessage.getStatus()).toString());
                    sendResponseMessage(wrongFirstMessage);
                }
                try {
                    auth(firstMessage);
                } catch (IllegalPasswordException | ClientNotFoundException e) {
                    LOGGER.warn(e.getLocalizedMessage());
                    sendResponseMessage(new Message(MessageStatus.DENIED)
                            .setText("Login or password is incorrect. Please, check your data"));
                }
            }
        } catch(JAXBException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        while (true) {
            try {
                handle(Message.from(in.readUTF()));
                lastInputMessage = LocalDateTime.now();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    sendResponseMessage(new Message(MessageStatus.ERROR).setText(e.getLocalizedMessage()));
                }catch (IOException e1){
                    LOGGER.error(e1.getLocalizedMessage());
                }
            }
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

    private void handle(Message message) throws Exception{
        if(message == null){
            throw new NullPointerException("Message must not be null");
        }
        switch (message.getStatus()) {
            case AUTH:
                auth(message);
                if(logged){
                    sendResponseMessage(new Message(MessageStatus.ACCEPTED));
                } else {
                    sendResponseMessage(new Message(MessageStatus.DENIED));
                }
                break;
            case REGISTRATION:
                Message response = registration(message);
                sendResponseMessage(response);
                break;
            case MESSAGE:
                RoomProcessing.sendMessage(server, message);
                break;
            case USERBAN:
                break;
            case CREATE_ROOM:
                createRoom(message);
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
    private void auth(Message message) throws IOException {
        if (message == null) {
            throw new NullPointerException("Message must not be null");
        }
        if (!MessageStatus.AUTH.equals(message.getStatus())) {
            throw new IOException(new StringBuilder("The message must be of status ")
                    .append(MessageStatus.AUTH).append(" but found ").append(message.getStatus()).toString());
        }
        if (message.getLogin() == null) {
            throw new IOException("Login has not been set");
        }
        if (message.getPassword() == null) {
            throw new IOException("Password has not been set");
        }
        File clientFolder = new File(server.getClientsDir(), String.valueOf(message.getLogin().hashCode()));
        File clientXml = new File(clientFolder, String.valueOf(message.getLogin().hashCode()).concat(".xml"));
        if (!clientFolder.isDirectory() || !clientXml.isFile()) {
            throw new ClientNotFoundException(new StringBuilder("Unable to find client ")
                    .append(message.getLogin()).toString());
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Client client = (Client) unmarshaller.unmarshal(clientXml);
            logged = client.getPassword().equals(message.getPassword());
        } catch (JAXBException e) {
            LOGGER.fatal(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    private Message registration(Message message) {
        if(message == null){
            try{
                throw new NullPointerException("Message must not be null");
            } catch (NullPointerException e){
                e.printStackTrace();
                throw e;
            }
        }
        File clientsDir = new File(server.getConfig().getProperty("clientsDir"));
        String login = message.getLogin();
        String password = message.getPassword();
        File clientFile = new File(new StringBuilder(clientsDir.getAbsolutePath()).append(login.hashCode())
                .append(".xml").toString());
        if(clientFile.exists()) {
            return new Message(MessageStatus.DENIED).setText(new StringBuilder("The login ")
                    .append(login).append(" is already taken").toString());
        }
        try{
            if(clientFile.createNewFile()) {
                Client client = new Client();
                client.setLogin(login);
                client.setPassword(password);
                client.setClientId(login.hashCode());
                JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
                Marshaller marshaller = jaxbContext.createMarshaller();
                marshaller.marshal(client,clientFile);
                return new Message(MessageStatus.ACCEPTED).setText(new StringBuilder("The account ")
                        .append(login).append(" has been successfully created").toString());
            }
        } catch (JAXBException | IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText(e.getLocalizedMessage());
        }
        return new Message(MessageStatus.DENIED).setText("Registration has not been finished successfully");
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
            Room room = RoomProcessing.createRoom(server.getConfig(), message.getFromId() , message.getToId());
            if (room == null) {
                return new Message(MessageStatus.ERROR).setText("Some error has occurred during the room creation");
            } else {
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