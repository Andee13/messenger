package server;

import javafx.collections.ObservableMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.*;

@XmlRootElement
public class Server extends Thread {
    @XmlElement
    private int port;
    private ObservableMap<Integer, ClientListener> clients;
    @XmlJavaTypeAdapter(FileAdapter.class)
    private static File clientsDir;
    @XmlJavaTypeAdapter(FileAdapter.class)
    private static File roomsDir;
    private Map<Integer, Room> onlineRooms;
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;

    public ObservableMap<Integer, ClientListener> getClients() {
        return clients;
    }

    public void setClients(ObservableMap<Integer, ClientListener> clients) {
        this.clients = clients;
    }

    public Map<Integer, Room> getOnlineRooms() {
        return onlineRooms;
    }

    public void setOnlineRooms(Map<Integer, Room> onlineRooms) {
        this.onlineRooms = onlineRooms;
    }

    private Server(){
        onlineRooms = new HashMap<>();
        File config = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("server.cfg").toString());
        if(!config.exists()){
            try {
                config.createNewFile();
                (new BufferedWriter(new FileWriter(config)))
                        .write(new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                                .append("<!DOCTYPE server [\n")
                                .append("        <!ELEMENT server (port)>\n")
                                .append("        <!ELEMENT port ANY>\n")
                                .append("        ]>\n")
                                .append("<server>\n")
                                .append("    <port>5940</port>\n")
                                .append("</server>")
                                .toString());
                // TODO create a tray notification
                System.out.println((new StringBuilder("Please, set the server parameters in the file ")
                        .append(config.getAbsolutePath()).append(" and restart the server")));
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
        // TODO logging the exceptions
        try {
            loadConfiguration(new FileInputStream(config));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws JAXBException {
        clientsDir = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("users").toString());
        // TODO logging about directory creation
        if(!clientsDir.exists()){
            if(!clientsDir.mkdir()){
                throw new RuntimeException(new StringBuilder("Unable to create a directory: ")
                        .append(clientsDir.getAbsolutePath()).toString());
            }
        }
        roomsDir = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("rooms").toString());
        // TODO logging about directory creation
        if(!roomsDir.exists()){
            if(!roomsDir.mkdir()){
                throw new RuntimeException(new StringBuilder("Unable to create a directory: ")
                        .append(roomsDir.getAbsolutePath()).toString());
            }
        }
        File serverConfig = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("server.xml").toString());
        /*
        * Here we set the default server properties
        * in case if server starts for the first time
        * */
        JAXBContext jaxbContext = JAXBContext.newInstance(Server.class);
        Server server;
        if (!serverConfig.exists()) {
            server = new Server();
            server.setPort(5940); // sets the default port number
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(server, serverConfig);
        } else {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            server = (Server) unmarshaller.unmarshal(serverConfig);
        }
        server.start();
    }

    // TODO logging the exceptions
    @Override
    public void run() {
        ServerSocket serverSocket = null;
        Socket socket = null;
        while (true) {
            try {
                ClientListener clientListener = new ClientListener(this, serverSocket.accept());
                clientListener.run();
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    // TODO logging exceptions
    public void close() throws IOException, JAXBException {
        for (Map.Entry<Integer, ClientListener> client : clients.entrySet()) {
            client.getValue().closeClientSession();
        }
        interrupt();
    }

    private void loadConfiguration(InputStream is)
            throws SAXException, IOException, ParserConfigurationException{
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setValidating(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        documentBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });

        Document document = documentBuilder.parse(new BufferedInputStream(is));
        Element root = document.getDocumentElement();
        port = Integer.parseInt(root.getElementsByTagName("port").item(0).getTextContent());
    }

    public void closeClientSession (ClientListener client) throws IOException, JAXBException {
        client.closeClientSession();
    }

    public static File getClientsDir() {
        return clientsDir;
    }

    public static File getRoomsDir() {
        return roomsDir;
    }

    public void runRoom(int id){
        if(id < 0){
            throw new IllegalArgumentException(new StringBuilder("Room id is expected to be greater than 0, but found: ")
                    .append(id).toString());
        }
        File roomFile = new File(new StringBuilder(roomsDir.getAbsolutePath())
                .append(String.valueOf(id)).append(".xml").toString());
    }

    private static class FileAdapter extends XmlAdapter<String, File> {
        @Override
        public File unmarshal(String pathname) throws Exception {
            return new File(pathname);
        }

        @Override
        public String marshal(File v) throws Exception {
            return v.getAbsolutePath();
        }
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public static void setClientsDir(File clientsDir) {
        Server.clientsDir = clientsDir;
    }

    public static void setRoomsDir(File roomsDir) {
        Server.roomsDir = roomsDir;
    }
}