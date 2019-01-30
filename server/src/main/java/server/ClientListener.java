package server;

import common.message.Message;
import common.message.XMLMessageBuilder;
import common.message.XMLMessageParser;
import common.message.status.RequestStatus;
import common.message.status.ResponseStatus;
import common.message.status.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;

public class ClientListener extends Thread{
    private Socket socket;
    private Server server;
    private DataOutputStream out;
    private DataInputStream in;
    private XMLMessageBuilder xmlMessageBuilder;
    private LocalDateTime lastInputMessage;
    private int lastRoomIdTextMessage = DEFAULT_VALUE;
    private int userId = DEFAULT_VALUE;
    private static final int DEFAULT_VALUE = -1;
    private boolean logged;
    private final LocalDateTime connected;

    public ClientListener(Server server, Socket socket) {
        this.server = server;
        this.socket = socket;
        connected = LocalDateTime.now();
        xmlMessageBuilder = new XMLMessageBuilder();
        try {
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
            try {
                server.closeClientSession(this);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    // TODO logging the exceptions
    @Override
    public void run() {
        while (true) {
            try {
                lastInputMessage = LocalDateTime.now();
                handle((new XMLMessageParser(in.readUTF())).parseInput().getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                xmlMessageBuilder.setMessage(new Message(ResponseStatus.ERROR).setException(e));
                xmlMessageBuilder.buildXML();
                try {
                    out.writeUTF(xmlMessageBuilder.getXmlText());
                } catch (IOException e1) {
                    e1.printStackTrace();
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
        if(message.getType() == Type.RESPONSE){
            throw new IOException(new StringBuilder("The message coming from client must be ")
                    .append(Type.REQUEST.toString()).append(" type").toString());
        }
        switch ((RequestStatus)(message.getStatus())){
            case AUTH:
                auth(message);
                break;
            case REGISTRATION:
                registration(message);
                break;
            case MESSAGE:
                break;
            case USERBAN:
                break;
            case CREATE_ROOM:
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

    private void auth(Message message) throws IOException, XMLStreamException {
        String login = message.getLogin();
        String password = message.getPassword();
        File userFile = new File(new StringBuilder(Server.getUsersDir().getAbsolutePath())
                .append(login)
                .append(".txt")
                .toString());
        if(userFile.exists()){
            XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
            XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(
                    new BufferedReader(new FileReader(userFile)));
            while (xmlStreamReader.hasNext()){
                int event = xmlStreamReader.next();
                if(event == XMLStreamConstants.START_ELEMENT &&
                        xmlStreamReader.getLocalName().equals("password")){
                    xmlStreamReader.next();
                    break;
                }
                xmlStreamReader.next();
                out.writeUTF((new XMLMessageBuilder()).setMessage(
                        (logged = xmlStreamReader.getText().equals(password)) ?
                                (new Message(ResponseStatus.ACCEPTED)) :
                                (new Message(ResponseStatus.DENIED))
                ).getXmlText());
                out.flush();
                return;
            }
            out.writeUTF((new XMLMessageBuilder())
                    .setMessage(new Message(ResponseStatus.DENIED))
                    .buildXML()
                    .getXmlText());
            out.flush();
        }
    }

    private void registration(Message message) throws IOException, ParserConfigurationException, TransformerException {
        if(message == null){
            throw new NullPointerException("Message must not be null");
        }
        String login = message.getLogin();
        String password = message.getPassword();
        File userFile = new File(new StringBuilder(Server.getUsersDir().getAbsolutePath())
                .append(login)
                .append(".txt")
                .toString());
        if(userFile.exists()){
            out.writeUTF((new XMLMessageBuilder()).setMessage(
                    new Message(ResponseStatus.DENIED)
                    .setText(new StringBuilder("The login ").append(login).append(" is already taken").toString())
            ).buildXML().getXmlText());
            out.flush();
            return;
        }
        if(userFile.createNewFile()){
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = documentBuilder.newDocument();
            Element user = document.createElement("user");
            Element loginTag = document.createElement("login");
            Element passwordTag = document.createElement("password");
            loginTag.setTextContent(login);
            passwordTag.setTextContent(password);
            user.appendChild(loginTag);
            user.appendChild(passwordTag);
            document.appendChild(user);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StreamResult streamResult = new StreamResult(userFile);
            transformer.transform(new DOMSource(document), streamResult);
            streamResult.getWriter().flush();
            logged = true;
            return;
        }
        out.writeUTF((new XMLMessageBuilder().setMessage(new Message(ResponseStatus.DENIED)).buildXML().getXmlText()));
        out.flush();
    }

    public LocalDateTime getConnected() {
        return LocalDateTime.from(connected);
    }
}