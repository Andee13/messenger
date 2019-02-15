package server;

import common.message.Message;
import common.message.status.MessageStatus;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.*;

@XmlRootElement
public class Server extends Thread {
    private ObservableMap<Integer, ClientListener> clients;
    private ObservableMap<Integer, Room> onlineRooms;
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger("Server");
    private Properties config;
    private static Properties defaultProperties;

    public Properties getConfig() {
        return config;
    }

    public File getRoomsDir() {
        return new File(config.getProperty("roomsDir"));
    }

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

    private Server(Properties serverConfig) {
        config = serverConfig;
    }

    /**
     *   If the server is being launched without parameters, it is thought
     * that you want to start it from the current folder.
     * Correct root folder structure is expected
     *
     *  In case, if the structure is not kept, the necessary folders and files will be created,
     * server will stop in order you are able to set the configurations.
     * */
    public static void main(String[] args) throws IOException{
        StartParameter startParameter = null;
        try {
            startParameter = parseInput(args);
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return;
        }
        File serverProperiesFile = null;
        try {
                serverProperiesFile = new File(new StringBuilder(new File(Main.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).getAbsolutePath())
                        .append(File.separatorChar).append("serverConfig.xml").toString());
        } catch (URISyntaxException e) {
                LOGGER.error(e.getLocalizedMessage());
        }
        switch (args.length) {
            case 2:
                serverProperiesFile = new File(args[1]);
                if (!arePropertiesValid(serverProperiesFile)) {
                    System.out.println(new StringBuilder("Invalid server properties file: ").append(args[1]).toString());
                } else {
                    startServer(serverProperiesFile);
                }
                break;
                default:
                    StringBuilder stringBuilder = new StringBuilder();
                    for(String arg : args){
                        stringBuilder.append(arg);
                    }
                    LOGGER.error(new StringBuilder("Invalid start arguments: ").append(stringBuilder));
                    return;
        }
        switch (startParameter) {
            case START:
                try {
                    startServer(serverProperiesFile);
                } catch (IOException e) {
                    LOGGER.fatal(e.getLocalizedMessage());
                    return;
                }
                break;
            case STOP:
                try{
                    stopServer(serverProperiesFile);
                } catch (IOException e) {
                    LOGGER.fatal(e.getLocalizedMessage());
                }
                break;
            case RESTART:
                break;
                default:
                    LOGGER.error(new StringBuilder("Unknown invocation mode: ").append(startParameter).toString());
                    return;
        }


    }

    /**
     *  The method {@code parseInput} decides what the server has to do depending on passed parameters
     *
     * @param           args is the program input parameters
     * */
    private static StartParameter parseInput(@NotNull String [] args) throws IOException{
        if(args.length == 0) {
            return StartParameter.START;
        } else {
            switch (args[0].toLowerCase()) {
                case "-start":
                    return StartParameter.START;
                case "-stop":
                    return StartParameter.STOP;
                case "-restart":
                    return StartParameter.RESTART;
                    default: throw new IOException(new StringBuilder("Unknown command: ").append(args[0]).toString());
            }
        }
    }

    /**
     *  The method {@code arePropertiesValid} checks if the passed abstract path is a valid file.
     * Returns {@code true} if and only if the specified by the abstract path file exists and contains
     * properties about existing clients and rooms directories, {@code false} otherwise.
     *
     * @param           properties a set of properties are to be validated
     *
     * @return          {@code true} if and only if the specified properties set contains all the necessary
     *                  configurations and they are valid i.e. it is possible to start a server using them,
     *                  {@code false} otherwise
     *
     * @exception       NullPointerException if {@code properties} is {@code null}
     * */
    public static boolean arePropertiesValid(Properties properties) {
        if (properties == null) {
            throw new NullPointerException("properties is null");
        }
        try {
            int port = Integer.parseInt(properties.getProperty("port"));
            if (port < 0 || port > 65536) {
                throw new IllegalArgumentException(
                        new StringBuilder("The port value was expected to be between 0 and 65536, but found ")
                                .append(port).toString());
            }
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
        if (!new File(properties.getProperty("roomsDir")).isDirectory()) {
            LOGGER.error(new StringBuilder("Invalid roomsDir value was set: ")
                    .append(properties.getProperty("roomsDir")));
            return false;
        }
        if (!new File(properties.getProperty("clientsDir")).isDirectory()) {
            LOGGER.error(new StringBuilder("Invalid clientsDir value was set: ")
                    .append(properties.getProperty("clientsDir")));
            return false;
        }
        return true;
    }

    /**
     *   The method creates an instance of {@code Property} and loads the properties from the specified file.
     *  The result is the same as a result of invocation {@code arePropertiesValid()}
     *
     * @param           propertyFile represents an abstract path to the file in which
     *                  properties of a server are set
     *
     * @return          {@code true} if and only if the specified abstract filepath  properties set contains
     *                  all the necessary configurations and they are valid i.e. it is possible
     *                  to start a server using them, {@code false} otherwise
     *
     * @exception       NullPointerException if {@code properties} is {@code null}
     * */
    public static boolean arePropertiesValid(File propertyFile) {
        if (propertyFile == null) {
            throw new NullPointerException("propertyFile is null");
        }
        if (propertyFile == null) {
            throw new NullPointerException();
        }
        Properties properties = new Properties();
        try {
            properties.load(new BufferedInputStream(new FileInputStream(propertyFile)));
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
        return arePropertiesValid(properties);
    }

    /**
     * @return          @code true} if and only if there are necessary files in the specified root folder of a server
     *
     * @param rootDir   the root folder of a server
     *
     * @exception       IllegalArgumentException if {@code rootDir} is not a folder
     * @exception       NullPointerException if the {@code rootDir} is {@code null}
     * */
    private static boolean isRootStructure(File rootDir) {
        if(rootDir == null) {
            throw new NullPointerException("The specified folder is not expected to be null");
        }
        File serverConfig = new File(new StringBuffer(rootDir.getAbsolutePath())
                .append(File.pathSeparator).append("serverConfig.xml").toString());
        File clientsDir = new File(new StringBuffer(rootDir.getAbsolutePath())
                .append(File.pathSeparator).append("clients").toString());
        File roomsDir = new File(new StringBuffer(rootDir.getAbsolutePath())
                .append(File.pathSeparator).append("rooms").toString());
        return serverConfig.isFile() && clientsDir.isDirectory() && roomsDir.isDirectory();
    }

    /**
     *  Organizes the necessary server root folder structure described above the {@code ServerConfig.class}
     * The method creates one of more demanded element. If there is not such one, the {@code createDefaultRootStructure}
     * creates it.
     *  The method does not delete or re-write already existing ones.
     *
     * @param           rootDir a server root folder
     *
     * @exception       IllegalArgumentException if the specified file is not a folder
     * */
    private static void createDefaultRootStructure(File rootDir) {
        if(rootDir == null) {
            throw new NullPointerException("The specified folder is not expected to be null");
        }
        if (!rootDir.isDirectory()) {
            throw new IllegalArgumentException("rootDir is expected to be an existing folder");
        }
        File serverConfig = new File(new StringBuffer(rootDir.getAbsolutePath())
                .append(File.separatorChar).append("serverConfig.xml").toString());
        File clientsDir = new File(new StringBuffer(rootDir.getAbsolutePath())
                .append(File.separatorChar).append("clients").toString());
        File roomsDir = new File(new StringBuffer(rootDir.getAbsolutePath())
                .append(File.separatorChar).append("rooms").toString());
        if (!serverConfig.isFile()) {
            LOGGER.info(new StringBuilder("Creating the default server configuration file: ")
                    .append(serverConfig.getAbsolutePath()));
            try {
                if(!serverConfig.createNewFile()){
                    throw new RuntimeException(new StringBuilder("Failed default server configuration file creation: ")
                            .append(serverConfig.getAbsolutePath()).toString());
                }
                Properties defaultProperties = getDefaultProperties();
                try(OutputStream os = new BufferedOutputStream(new FileOutputStream(serverConfig))){
                    defaultProperties.storeToXML(os,"This is a default properties","UTF-8");
                    LOGGER.info(new StringBuilder("The default properties have been stored in the file ")
                            .append(serverConfig.getAbsolutePath())
                            .append(". Please, set your server configuration there."));
                } catch (Exception e) {
                    LOGGER.fatal(e.getLocalizedMessage());
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (!clientsDir.isDirectory()) {
            LOGGER.info(new StringBuilder("Creating the clients folder: ")
                    .append(clientsDir.getAbsolutePath()));
            try {
                if(!serverConfig.createNewFile()){
                    throw new RuntimeException(new StringBuilder("Unable to create a clients folder: ")
                            .append(clientsDir.getAbsolutePath()).toString());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (!roomsDir.isDirectory()) {
            LOGGER.info(new StringBuilder("Creating the rooms folder: ")
                    .append(roomsDir.getAbsolutePath()));
            try {
                if(!serverConfig.createNewFile()){
                    throw new RuntimeException(new StringBuilder("Unable to create a clients folder: ")
                            .append(roomsDir.getAbsolutePath()).toString());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


    }

    /**
     *  The method {@code getDefaultProperties} returns
     * the default properties pattern for all servers.
     * */
    private static Properties getDefaultProperties() {
        if(defaultProperties == null) {
            Properties properties = new Properties();
            properties.setProperty("port", "5940");
            properties.setProperty("rcon","change_me");
            properties.setProperty("clientsDir", new StringBuilder("change")
                    .append(File.separatorChar).append("the")
                    .append(File.separatorChar).append("clients")
                    .append(File.separatorChar).append("folder")
                    .append(File.separatorChar).append("path")
                    .toString()
            );
            properties.setProperty("roomsDir", new StringBuilder("change")
                    .append(File.separatorChar).append("the")
                    .append(File.separatorChar).append("rooms")
                    .append(File.separatorChar).append("folder")
                    .append(File.separatorChar).append("path")
                    .toString()
            );
            defaultProperties = properties;
        }
        return defaultProperties;
    }

    private static Properties copyDefaultProperties() {
        return new Properties(getDefaultProperties());
    }

    /**
     * The method {@code startServer} starts the server denoted by the specified {@code serverPropertiesFile}
     *
     * @throws          IOException if an I/O error occures
     * */
    private static void startServer(File serverPropertiesFile) throws IOException{
        if(serverPropertiesFile == null) {
            throw new NullPointerException("serverPropertiesFile must not be null");
        }
        if(!arePropertiesValid(serverPropertiesFile)) {
            throw new IOException("Invalid server properties file");
        }
        Properties serverProperties = new Properties();
        serverProperties.load(new BufferedInputStream(new FileInputStream(serverPropertiesFile)));
        startServer(serverProperties);
    }

    /**
     * The method {@code startServer} starts the server denoted by the specified {@code serverProperties}
     *
     * @throws          IOException if an I/O error occures
     *
     * @exception       IllegalStateException if the server denoted by the specified {@code serverProperties}
     *                  has already been launched or the port set in the {@code serverProperties} is taken
     * */
    private static void startServer(Properties serverProperties) throws IOException{
        if(serverProperties == null) {
            throw new NullPointerException("The server properties must not be null");
        }
        if(!arePropertiesValid(serverProperties)) {
            throw new IOException("The server properties are not valid");
        }
        // TODO checking whether the port is already taken
        //
        // TODO creating the server with the properties
        Server server = new Server(serverProperties);
        server.start();
        LOGGER.info(new StringBuilder("Server thread status: ").append(server.getState()).toString());
    }

    // TODO create a method which checks whether the port is taken

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

    /**
     * The method {@code stopServer} stops the server denoted by the specified {@code serverProperties}
     *
     * @throws          IOException if the {@code serverProperties} are invalid
     *
     * @throws          IllegalStateException if the server denoted by the specified properties
     *                                        has not been launched on this {@code localhost} yet
     * */
    public static void stopServer(Properties serverProperties) throws IOException, IllegalStateException {
        if(serverProperties == null) {
            throw new NullPointerException("The serverProperties must not be null");
        }
        if(!arePropertiesValid(serverProperties)) {
            throw new IOException("The server properties are not valid");
        }
        // TODO checking whether the server is not launched
        LOGGER.info("Stopping the server in processing");
        Socket socket = new Socket("localhost", Integer.parseInt(serverProperties.getProperty("port")));
        Message stopServerMessage = new Message(MessageStatus.STOP_SERVER).setPassword(serverProperties.getProperty("rcon"));
        // TODO stopping the server
    }

    /**
     *
     * */
    public static void stopServer(File serverPropertiesFile) throws IOException, IllegalStateException {
        // TODO stopping the server
    }

    /**
     *
     * */
    public void closeClientSession (ClientListener client) throws IOException, JAXBException {
        client.closeClientSession();
    }

    /**
     *
     * */
    public void runRoom(int id){
        if (config == null) {
            throw new IllegalStateException("The configurations have not been set yet");
        }
        if(id < 0){
            throw new IllegalArgumentException(new StringBuilder("Room id is expected to be greater than 0, but found: ")
                    .append(id).toString());
        }
        File roomFile = new File(new StringBuilder((new File(config.getProperty("roomsDir"))).getAbsolutePath())
                .append(String.valueOf(id)).append(".xml").toString());
    }

    /**
     *  The method {@code clientExists} informs whether a client with the specified {@code clientId} has been registered
     *
     * @param           clientId is the id to be searched for
     *
     * @return          {@code true} if and only if the client denoted by this {@code clientId} has been registered
     *                  and the file with his data is a normal file {@code false} otherwise
     * */
    public boolean clientExists(int clientId) {
        File file = new File(
                new StringBuilder((new File(config.getProperty("clientsDir")))
                        .getAbsolutePath()).append(clientId).append(".xml").toString()
        );
        return file.isFile();
    }
}