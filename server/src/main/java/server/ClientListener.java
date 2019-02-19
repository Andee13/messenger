package server;

import common.Room;
import common.RoomProcessing;
import common.message.Message;
import common.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.InputSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import server.exceptions.IllegalPasswordException;
import server.exceptions.NoSuchClientException;

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

    private final Logger LOGGER = Logger.getLogger("ClientListener");

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
        try{

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
                } catch (IllegalPasswordException | NoSuchClientException e) {
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
                // TODO handle the input
                in.read();
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
        /*if(message == null){
            throw new NullPointerException("Message must not be null");
        }*/
        switch ((MessageStatus)(message.getStatus())){
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
                sendTextMessage(message);
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
     * @exception       NoSuchClientException if the specified client's file has not been found
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
            throw new NoSuchClientException(new StringBuilder("Unable to find client ")
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

    // TODO remove the JAXBException
    private Message createRoom(@NotNull Message message) throws IOException, JAXBException {
        if (!message.getStatus().equals(MessageStatus.CREATE_ROOM)) {
            return new Message(MessageStatus.ERROR)
                    .setText(new StringBuilder("The message status must be ").append(MessageStatus.CREATE_ROOM)
                            .append(" but found ").append(message.getStatus()).toString());
        }
        /*
        * The field toId is considered as an id of the initial room member, thus it must be valid
        * i.e. the client with such id must exists
        * */
        Room room = RoomProcessing.createRoom(server.getConfig(), message.getFromId() , message.getToId());
        if (room == null){
            return new Message(MessageStatus.ERROR).setText("Some error has occurred during the room creation");
        } else {
            return new Message(MessageStatus.ACCEPTED).setRoomId(room.getRoomId())
                    .setText(new StringBuilder("The room id: ").append(room.getRoomId())
                            .append(" has been successfully created").toString());
        }

    }

    // TODO sending a message to the specific room
    private void sendTextMessage(Message message) {
        /*if(!message.getStatus().equals(MessageStatus.MESSAGE)){
            throw new IllegalArgumentException(new StringBuilder("Status ")
                    .append(MessageStatus.MESSAGE)
                    .append(" is expected, but found: ")
                    .append(message.getStatus()).toString());
        }
        if(message.getFromId() != userId){
            throw new IllegalAccessException("Clients id mismatch");
        }
        if(message.getToId() != )*/
    }

    public LocalDateTime getConnected() {
        return LocalDateTime.from(connected);
    }

    // TODO resolve the JAXBException
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

    public void saveClient() {
        if(client == null) {
            // TODO decide what to do with a case when the client hasn't been set
            return;
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            File clientFile = new File(new StringBuilder(server.getConfig().getProperty("clientsDir"))
                    .append(File.pathSeparator).append(client.getClientId()).append(".xml").toString());
            marshaller.marshal(client, clientFile);
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    public void closeClientSession() throws IOException {
        if(isAlive() && !isInterrupted()){
            in.close();
            out.close();
            socket.close();
            if (logged) {

            }
            saveClient();
            server.getClients().remove(this);
            interrupt();
        }
    }
}