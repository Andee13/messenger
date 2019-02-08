package server;

import javafx.beans.property.adapter.JavaBeanStringPropertyBuilder;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.bind.*;
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
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@XmlRootElement
public class Server extends Thread {
    @XmlElement
    private int port;
    private ObservableMap<Integer, ClientListener> clients;
    @XmlJavaTypeAdapter(FileAdapter.class)
    private static File clientsDir;
    @XmlJavaTypeAdapter(FileAdapter.class)
    private static File roomsDir;
    private ObservableMap<Integer, Room> onlineRooms;
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger("Server");
    private static Server server;
    private static Client admin;

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
        this.onlineRooms = FXCollections.observableMap(onlineRooms);
    }

    private Server(){
        onlineRooms = FXCollections.observableMap(new TreeMap<>());
        clients = FXCollections.observableMap(new TreeMap<>());
    }

    private static void startServer() {

        File serverConfig = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("server.xml").toString());
        /*
         * Here we set the default server properties
         * in case if server starts for the first time
         * */
        try {
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
                LOGGER.info(new StringBuilder("Server configuration file ")
                        .append(serverConfig.getAbsolutePath()).append(" has been successfully created").toString());
            }
            server.start();
            Server.server = server;
            LOGGER.info("Server has been launched");
        } catch (JAXBException e) {
            LOGGER.error(e);
            throw new RuntimeException(e);
        }

    }

    private static void printCommandsList(){
        System.out.println("The server commands: ");
        System.out.println("\uD836\uDD11 start to start the server with current configurations");
        System.out.println("\uD836\uDD11 stop to stop the server");
        System.out.println("\uD836\uDD11 restart to restart the server");
        System.out.println("\uD836\uDD11 login [your_login_here] [your password here] to login as an admin");
    }

    private static void handle(String command){
        String [] commandParts = command.split("\\W+");
        if(commandParts.length == 0) {
            return;
        }
        switch (commandParts[0]) {
            case "help" :
                printCommandsList();
                break;
            case "start" :
                if(server != null) {
                    System.out.println("The server is running");
                } else {
                    startServer();
                }
                break;
            case "stop":
                if (server != null && (Thread.State.RUNNABLE.equals(server.getState()))) {
                    try {
                        server.stopServer();
                    } catch (IOException e) {
                        LOGGER.fatal(e.getLocalizedMessage());
                    }
                } else {
                    System.out.println("The server has not been started yet");
                    System.out.println("Print help to see the available commands");
                }
            case "restart" :
                if (server != null && (Thread.State.RUNNABLE.equals(server.getState()))){
                    handle("stop");
                    server = null;
                    handle("start");
                } else {
                    System.out.println("The server has not been launched yet");
                }
                break;
            case "login" :
                Pattern pattern = Pattern.compile("^login (\\S+){1} (\\S+){1}$");
                Matcher matcher = pattern.matcher(command);
                if (!matcher.matches()) {
                    System.out.println("Please, enter login using the following format:\n" +
                            "login [here_your_login] [here_your_password]");
                    return;
                }
                String login = matcher.group(1);
                String password = matcher.group(2);
                if(clientExists(login.hashCode())) {
                    try {
                        JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
                        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                        Client client = (Client) unmarshaller.unmarshal(new File(new StringBuilder(
                                clientsDir.getAbsolutePath()).append(login.hashCode()).append(".xml").toString()));
                        if(client.isAdmin() && password.equals(client.getPassword())) {
                            admin = client;
                            System.out.println(new StringBuilder("Welcome, ").append(admin.getLogin()));
                            break;
                        }
                    } catch (JAXBException e) {
                        LOGGER.fatal(e.getLocalizedMessage());
                        break;
                    }
                }
                System.out.println("Check your login and password, please");
                break;
                default:
                    System.out.println(new StringBuilder("Unable to execute command: \"").append(command).append('"'));
                    printCommandsList();
                    break;
        }
    }

    public static void main(String[] args) {
        clientsDir = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("users").toString());

        //System.err.println(clientsDir.getAbsolutePath());
        /*for(int i = 0; i < clientsDir.getAbsolutePath().length(); i++){
            System.out.println(clientsDir.getAbsolutePath().charAt(i));
        }
        System.out.println(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        new Scanner(System.in).nextLine();
        */
        if(!clientsDir.exists()){
            if(!clientsDir.mkdir()){
                throw new RuntimeException(new StringBuilder("Unable to create a directory: ")
                        .append(clientsDir.getAbsolutePath()).toString());
            }
            LOGGER.info(new StringBuilder("The clients directory ")
                    .append(clientsDir.getAbsolutePath()).append(" has been created").toString());
        }
        roomsDir = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("rooms").toString());
        if(!roomsDir.exists()){
            if(!roomsDir.mkdir()){
                throw new RuntimeException(new StringBuilder("Unable to create a directory: ")
                        .append(roomsDir.getAbsolutePath()).toString());
            }
            LOGGER.info(new StringBuilder("The rooms directory ")
                    .append(roomsDir.getAbsolutePath()).append(" has been created").toString());
        }
        Scanner scanner = new Scanner(System.in);
        System.out.println("Hello, please, enter commands here");
        while (true) {
            String comand = scanner.nextLine();
            if(comand == null){
                continue;
            }
            handle(comand);
        }
    }

    @Override
    public void run() {
        ServerSocket serverSocket = null;
        Socket socket;
        while (true) {
            try {
                socket = serverSocket.accept();
                LOGGER.info(new StringBuilder("Incoming connection from: ")
                        .append(socket.getInetAddress()).toString());
                ClientListener clientListener = new ClientListener(this, socket);
                clientListener.run();
            } catch (IOException e) {
               LOGGER.error(e.getLocalizedMessage());
            }
        }
    }

    public void stopServer() throws IOException {
        LOGGER.info("Stopping the server");
        for (Map.Entry<Integer, ClientListener> client : clients.entrySet()) {
            client.getValue().closeClientSession();
        }
        interrupt();
    }

    private void loadConfiguration(InputStream is) throws SAXException, IOException, ParserConfigurationException {
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

    private void setPort(int port) {
        this.port = port;
    }

    public static void setClientsDir(File clientsDir) {
        Server.clientsDir = clientsDir;
    }

    public static void setRoomsDir(File roomsDir) {
        Server.roomsDir = roomsDir;
    }

    /**
     * The method {@code clientExists} informs whether a client with the specified {@code clientId} has been registered
     *
     * @param clientId is the id to be searched for
     *
     * @return {@code true} if and only if the client denoted by this {@code clientId} has been registered
     *                      and the file with his data is a normal file {@code false} otherwise
     * */
    public static boolean clientExists(int clientId){
        File file = new File(
                new StringBuilder(clientsDir.getAbsolutePath()).append(clientId).append(".xml").toString()
        );
        return file.isFile();
    }
}