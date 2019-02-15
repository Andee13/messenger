package server;

import common.message.Message;
import common.message.status.MessageStatus;
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

public class ClientListener extends Thread{
    private Socket socket;
    private Server server;
    private DataOutputStream out;
    private DataInputStream in;
    private LocalDateTime lastInputMessage;
    private boolean logged;
    private LocalDateTime connected;
    private Client client;

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
        JAXBContext jaxbContext = null;
        try{
            jaxbContext = JAXBContext.newInstance(Message.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            while (!logged) {
                Message firstMessage = (Message) unmarshaller.unmarshal(new InputSource(new StringReader(in.readUTF())));
                if(!MessageStatus.AUTH.equals(firstMessage.getStatus()) && !MessageStatus.REGISTRATION.equals(firstMessage.getStatus())){
                    Message wrongFirstMessage = new Message(MessageStatus.ERROR)
                            .setText(new StringBuilder("The first message must be either ")
                                    .append(MessageStatus.AUTH).append(" or ").append(MessageStatus.REGISTRATION)
                                    .append(" status. But found ").append(firstMessage.getStatus()).toString());
                    Marshaller marshaller = jaxbContext.createMarshaller();
                    StringWriter stringWriter = new StringWriter();
                    marshaller.marshal(wrongFirstMessage, stringWriter);
                    out.writeUTF(stringWriter.toString());
                }
                try {
                    auth(firstMessage);
                } catch (XMLStreamException e) {
                    LOGGER.error(e);
                    sendResponseMessage(new Message(MessageStatus.ERROR).setText(e.getLocalizedMessage()));
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
                }catch (JAXBException | IOException e1){
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

    private void auth(Message message) throws IOException, XMLStreamException, JAXBException {
        String login = message.getLogin();
        String password = message.getPassword();
        File clientFile = new File(new StringBuilder(server.getConfig().getProperty("clientsDir"))
                .append(File.pathSeparator).append(login.hashCode()).append(".xml").toString());
        if(clientFile.exists()){
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(
                    new BufferedReader(new FileReader(clientFile)));
            while (xmlStreamReader.hasNext()){
                int event = xmlStreamReader.next();
                if(event == XMLStreamConstants.START_ELEMENT &&
                        xmlStreamReader.getLocalName().equals("password")){
                    xmlStreamReader.next();
                    break;
                }
                xmlStreamReader.next();
                logged = xmlStreamReader.getText().equals(password);
                return;
            }
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
    public void sendResponseMessage(Message message) throws IOException, JAXBException{
        JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        StringWriter stringWriter = new StringWriter();
        marshaller.marshal(message, stringWriter);
        out.writeUTF(stringWriter.toString());
        out.flush();
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
        if(isAlive() && ! isInterrupted()){
            in.close();
            out.close();
            socket.close();
            saveClient();
            server.getClients().remove(this);
            interrupt();
        }
    }
}