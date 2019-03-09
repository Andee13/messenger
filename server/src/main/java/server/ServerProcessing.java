package server;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import server.client.ClientListener;
import server.room.Room;

import javax.security.auth.login.FailedLoginException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;

import static common.Utils.buildMessage;

/**
 *  This class contains methods which operates with an instance of {@code Server}
 *  e.g. starts a server, stops it or restarts
 *
 * @see Server
 * */
public class ServerProcessing {

    private static final Logger LOGGER = Logger.getLogger("ServerProcessing");
    private static Properties defaultProperties;
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    public static final int MESSAGE_HISTORY_DIMENSION = 100;

    /**
     *   If the server is being launched without parameters, it is thought
     * that you want to start it from the current folder.
     * Correct root folder structure is expected
     *
     *  In case, if the structure is not kept, the necessary folders and files will be created,
     * server will stop in order you are able to set the configurations.
     *
     * @param           args server start directives
     *
     * @throws          IOException in case if user entered wrong parameters
     * */
    public static void main(String[] args) throws IOException {
        InvocationMode invocationMode;
        try {
            invocationMode = getInvocationMode(args);
        } catch (IOException e) {
            printCommands();
            return;
        }
        File currentFolder;
        File serverProperiesFile;
        // setting the default server root folder
        try {
            currentFolder = new File(ServerProcessing.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            LOGGER.error(e.getLocalizedMessage());
            return;
        }
        try {
            serverProperiesFile = new File(args[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            serverProperiesFile = new File(currentFolder, "serverConfig.xml");
        }
        Properties serverProperties;
        switch (invocationMode) {
            case START:
                try {
                    startServer(serverProperiesFile);
                } catch (IOException e) {
                    LOGGER.error(e.getLocalizedMessage());
                    return;
                }
                break;
            case STOP:
                try {
                    sendStopServerMessage(serverProperiesFile);
                } catch (Exception e) {
                    LOGGER.error(e.getLocalizedMessage());
                }
                break;
            case RESTART:
                break;
            case CREATE_DEFAULT_SERVER:
                try {
                    createDefaultRootStructure(new File(args[1]));
                } catch (ArrayIndexOutOfBoundsException e) {
                    LOGGER.error("Unspecified root folder path");
                    return;
                }
                break;
            case BAN:
                try {
                    serverProperties = new Properties();
                    serverProperties.loadFromXML(new FileInputStream(serverProperiesFile));
                    clientBan(serverProperties, args[2], true, Integer.parseInt(args[3]));
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Not all arguments are specified. Please, check the input");
                    printCommands();
                } catch (NumberFormatException e) {
                    System.out.println("Wrong number of hours entered : ".concat(args[3]));
                }
                break;
            case UNBAN:
                try {
                    serverProperties = new Properties();
                    serverProperties.loadFromXML(new FileInputStream(serverProperiesFile));
                    clientBan(serverProperties, args[2], false, 0);
                } catch (IndexOutOfBoundsException e) {
                    System.out.println("Not all arguments are specified. Please, check the input");
                    printCommands();
                }
                break;
            default:
                String errorMessage = "Unknown invocation mode: ".concat(String.valueOf(invocationMode));
                LOGGER.error(errorMessage);
                throw new IOException(errorMessage);
        }
    }

    /**
     * This method is used to ban/unban a client having login like {@code} login. It just sends a message to server
     * and prints a response. It does not guarantees that client has been banned/unbanned
     *
     * @param           ban set is {@code true}
     *
     * */
    public static void clientBan(Properties serverProperties, String login, boolean ban, int hours) {
        if (ban && hours < 1) {
            throw new IllegalArgumentException("hours: positive integer expected, but found "
                    .concat(String.valueOf(hours)));
        }
        try (Socket socket = new Socket("localhost", Integer.parseInt(serverProperties.getProperty("port")));
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(3000);
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            StringWriter stringWriter = new StringWriter();
            Message banMessage = new Message(ban ? MessageStatus.CLIENTBAN : MessageStatus.CLIENTUNBAN)
                    .setToId(login.hashCode())
                    .setLogin(serverProperties.getProperty("server_login"))
                    .setPassword(serverProperties.getProperty("server_password"));
            if (ban) {
                banMessage.setText(ServerProcessing.DATE_TIME_FORMATTER.format(LocalDateTime.now().plusHours(hours)));
            }
            marshaller.marshal(banMessage, stringWriter);
            dataOutputStream.writeUTF(stringWriter.toString());
            LOGGER.info(buildMessage("Server response:\n", dataInputStream.readUTF()));
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        } catch (SocketTimeoutException e) {
            LOGGER.error("Server does not response");
        } catch (IOException e) {
            LOGGER.error(buildMessage(e.getClass().getName(), e.getLocalizedMessage()));
        }
    }

    private static boolean isServerLaunched(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setSoTimeout(1000);
            serverSocket.accept();
            return true;
        }catch (SocketTimeoutException e) {
            return false;
        } catch (BindException e) {
            try {
                sendAndWait(port, 3);
            } catch (SocketTimeoutException e1) {
                return false;
            }
            return false;
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
    }

    private static void printCommands() {
        System.out.println("            <---Avaliable commands--->");
        System.out.println("-cds path/to/server/root/folder                 - to create a default server root structure in the specified folder");
        System.out.println("-start path/to/serverConfig.xml                 - to start the server denoted by the configurations");
        System.out.println("-restart path/to/serverConfig.xml               - to restart the server denoted by the configurations");
        System.out.println("-stop path/to/serverConfig.xml                  - to stop the server denoted by the configurations");
        System.out.println("-ban path/to/serverConfig.xml <login> <hours>   - to ban the client on the server denoted by the configurations");
        System.out.println("-unban path/to/serverConfig.xml <login>         - to unban the client on the server denoted by the configurations");
        System.out.println(                     "<---    --->");

    }

    /**
     *  The method {@code getInvocationMode} decides what the server has to do depending on passed parameters
     *
     * @param           args is the program input parameters
     * */
    private static InvocationMode getInvocationMode(@NotNull String [] args) throws IOException {
        if(args.length == 0) {
            return InvocationMode.START;
        } else {
            switch (args[0].toLowerCase()) {
                case "-start":
                    return InvocationMode.START;
                case "-stop":
                    return InvocationMode.STOP;
                case "-restart":
                    return InvocationMode.RESTART;
                case "-cds" :
                    return InvocationMode.CREATE_DEFAULT_SERVER;
                case "-ban":
                    return InvocationMode.BAN;
                case "-unban":
                    return  InvocationMode.UNBAN;

                default: throw new IOException(buildMessage("Unknown command:", args[0]));
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
     * */
    public static boolean arePropertiesValid(@NotNull Properties properties) {
        if (properties == null) {
            return false;
        }
        try {
            int port = Integer.parseInt(properties.getProperty("port"));
            if (port < 0 || port > 65536) {
                LOGGER.error(buildMessage("The port value was expected to be between 0 and 65536"
                        ,"but found", port));
            }
        } catch (NumberFormatException e) {
            LOGGER.warn(buildMessage("Unable to extract a port number from server configuration",
                    properties.getProperty("port")));
            return false;
        }
        if (!new File(properties.getProperty("roomsDir")).isDirectory()) {
            LOGGER.warn(buildMessage("Invalid roomsDir value was set:", properties.getProperty("roomsDir")));
            return false;
        }
        if (!new File(properties.getProperty("clientsDir")).isDirectory()) {
            LOGGER.warn(buildMessage("Invalid clientsDir value was set:", properties.getProperty("clientsDir")));
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
     * */
    public static boolean arePropertiesValid(@NotNull File propertyFile) {
        if (propertyFile == null) {
            return false;
        }
        if(!propertyFile.isFile()) {
            return false;
        }
        Properties properties = new Properties();
        try {
            properties.loadFromXML(new BufferedInputStream(new FileInputStream(propertyFile)));
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
    private static boolean isRootStructure(@NotNull File rootDir) {
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
            LOGGER.warn("root folder is null");
            throw new NullPointerException("The specified folder is not expected to be null");
        }
        if (!rootDir.isDirectory()) {
            throw new IllegalArgumentException("rootDir is expected to be an existing folder");
        }
        File logsDir = new File(rootDir, "logs");
        File roomsDir = new File(rootDir, "rooms");
        File clientsDir = new File(rootDir, "clients");
        File serverConfig = new File(rootDir, "serverConfig.xml");
        File commonChatDir = new File(roomsDir, "0");
        File commonChatFile = new File(commonChatDir, "0.xml");
        if (!serverConfig.isFile()) {
            LOGGER.info(buildMessage("Creating the default server configuration file:",
                    serverConfig.getAbsolutePath()));
            try {
                if(!serverConfig.createNewFile()){
                    throw new RuntimeException(buildMessage("Failed default server configuration file creation:",
                            serverConfig.getAbsolutePath()));
                }
                Properties defaultProperties = getDefaultProperties();
                defaultProperties.setProperty("roomsDir", roomsDir.getAbsolutePath());
                defaultProperties.setProperty("clientsDir", clientsDir.getAbsolutePath());
                defaultProperties.setProperty("logsDir", clientsDir.getAbsolutePath());
                defaultProperties.setProperty("serverConfig", serverConfig.getAbsolutePath());
                try(FileOutputStream fos = new FileOutputStream(serverConfig)) {
                    defaultProperties.storeToXML(fos,null);
                    LOGGER.info(buildMessage("The default properties have been stored in the file",
                            serverConfig.getAbsolutePath(), ". Please, set your server configuration there."));
                } catch (Exception e) {
                    LOGGER.fatal(e.getLocalizedMessage());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (!clientsDir.isDirectory()) {
            LOGGER.info(buildMessage("Creating the clients folder:", clientsDir.getAbsolutePath()));
            if(!clientsDir.mkdir()){
                throw new RuntimeException("Unable to create a clients folder: ".concat(clientsDir.getAbsolutePath()));
            }
        }
        if (!roomsDir.isDirectory()) {
            LOGGER.info(buildMessage("Creating the rooms folder:", roomsDir.getAbsolutePath()));
            if(!roomsDir.mkdir()){
                throw new RuntimeException(buildMessage("Unable to create a clients folder:",
                        roomsDir.getAbsolutePath()));
            }
        }
        if (!logsDir.isDirectory()) {
            LOGGER.info(buildMessage("Creating the logs folder:", logsDir.getAbsolutePath()));
            if(!logsDir.mkdir()){
                throw new RuntimeException("Unable to create a logs folder:".concat(logsDir.getAbsolutePath()));
            }
        }
        if (!commonChatDir.isDirectory()) {
            LOGGER.info(buildMessage("Creating the common chat-room folder:", commonChatDir.getAbsolutePath()));
            if(!commonChatDir.mkdir()){
                throw new RuntimeException("Unable to create a common chat-room folder:"
                        .concat(logsDir.getAbsolutePath()));
            }
        }
        try {
            if (!commonChatFile.isFile()) {
                LOGGER.info(buildMessage("Creating the common chat-room file:",
                        commonChatFile.getAbsolutePath()));
                if(!commonChatFile.createNewFile()){
                    throw new RuntimeException("Unable to create a common chat-room file:"
                            .concat(logsDir.getAbsolutePath()));
                }
            }
        } catch (IOException e) {
            LOGGER.error("Unknown error:".concat(e.getLocalizedMessage()));
            throw new RuntimeException(e);
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(commonChatFile)) {
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            Room room = new Room();
            room.setRoomId(0);
            room.setAdminId("God".hashCode());
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(room, fileOutputStream);
            fileOutputStream.flush();
        } catch (FileNotFoundException e) {
            LOGGER.error("Unable to find the file: ".concat(commonChatFile.getAbsolutePath()));
            throw new RuntimeException(e);
        } catch (JAXBException | IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     *  The method {@code getDefaultProperties} returns
     * the default properties pattern for all servers.
     * */
    private static Properties getDefaultProperties() {
        if(defaultProperties == null) {
            Properties properties = new Properties();
            // a port number on which the server will be started
            properties.setProperty("port", "5940");
            // a server
            properties.setProperty("server_login", "God");
            properties.setProperty("server_password","change_me");
            // a path to the folder where clients' data will be stored
            properties.setProperty("clientsDir", buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "clients",
                    File.separatorChar, "folder", File.separatorChar, "path")
            );
            // a path to the folder where the rooms' data will be stored
            properties.setProperty("roomsDir", buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "rooms",
                    File.separatorChar, "folder", File.separatorChar, "path")
            );
            // folder for logs
            properties.setProperty("logsDir",buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "logs",
                    File.separatorChar, "folder", File.separatorChar, "path")
            );
            // setting the folder where the server configuration file will be stored
            properties.setProperty("serverConfig",buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "server",
                    File.separatorChar, "config", File.separatorChar, "path",
                    File.separatorChar, "serverConfig.xml")
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
     * @throws          IOException if an I/O error occurs
     *
     * @exception       IllegalStateException if the server denoted by the specified {@code serverPropertiesFile}
     *                  has already been launched or the port set in the {@code serverPropertiesFile} is taken
     * */
    private static void startServer(@NotNull File serverPropertiesFile) throws IOException {
        if (!arePropertiesValid(serverPropertiesFile)) {
            throw new IOException("The server properties are not valid");
        }
        Server server = new Server(serverPropertiesFile);
        server.start();
        LOGGER.info(buildMessage("Server thread status:", server.getState()));
    }

    private static void startServer(Properties serverConfiguration) throws IOException {
        startServer(new File(serverConfiguration.getProperty("serverConfig")));
    }

    /**
     *  The method {@code isServerLaunched} provides with information whether the server, specified by the
     * {@code serverProperties} is currently being launchd on localhost
     *
     * @param           serverProperties the configuration of a server
     *
     * @return          {@code true} if and only if the server, specified by the {@code serverProperties} exists and
     *                  is currently working i.e. the port the server is launched is not free for listening
     *                  {@code false} otherwise
     * */
    private static boolean isServerLaunched(Properties serverProperties) {
        int port = Integer.parseInt(serverProperties.getProperty("port"));
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            return false;
        } catch (BindException e) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     *  The method {@code isServerLaunched} is no more than unpacker that returns the result of invoking the
     * {@code isServerLaunched(Properties serverProperties)} method
     *
     * @param           serverPropertiesFile the file containing server configuration
     *
     * @return          {@code true} if and only if the server, specified by the {@code serverProperties} exists and
     *                  is currently working i.e. the port the server is launched is not free for listening
     *                  {@code false} otherwise
     * */
    private static boolean isServerLaunched(File serverPropertiesFile) {
        Properties serverProperties = new Properties();
        try(FileInputStream fileInputStream = new FileInputStream(serverPropertiesFile)) {
            serverProperties.loadFromXML(fileInputStream);
            return isServerLaunched(serverProperties);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     *  The method {@code sendStopServerMessage} stops the server specified by the properties
     * */
    public static void sendStopServerMessage(@NotNull Properties serverProperties) {
        if (!arePropertiesValid(serverProperties)) {
            LOGGER.error(buildMessage("Invalid server properties passed", serverProperties));
            return;
        }
        try (Socket socket = new Socket("localhost", Integer.parseInt(serverProperties.getProperty("port")));
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {
            try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(serverProperties.getProperty("port")))) {
                serverSocket.setSoTimeout(5000);
                serverSocket.accept();
                return;
            } catch (SocketTimeoutException e) {
                LOGGER.info(buildMessage("The server localhost:", serverProperties.getProperty("port"),
                        "is currently not active"));
                return;
            } catch (SocketException e) {
                LOGGER.info("The server is launched");
            }
            Message message = new Message(MessageStatus.STOP_SERVER)
                    .setPassword(serverProperties.getProperty("server_password"))
                    .setLogin(serverProperties.getProperty("server_login"));
            socket.setSoTimeout(10000);
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(message, stringWriter);
            dataOutputStream.writeUTF(stringWriter.toString());
            dataOutputStream.flush();

            LOGGER.info(buildMessage("The Message of", MessageStatus.STOP_SERVER,
                    "status has been sent to address localhost:", serverProperties.getProperty("port")));
        } catch (IOException | JAXBException e) {
            LOGGER.fatal(e.getMessage());
            return;
        }
    }

    /**
     *  The method {@code sendStopServerMessage} is just an interagent who unpacks server properties from the specified file
     * and invokes {@code sendStopServerMessage(Properties serverProperties)}
     *
     * @param           serverPropertiesFile the file which stores server properties
     *
     * @throws          IOException in case if {@code serverPropertiesFile} does not contains valid server configuration,
     *                  if an I/O error occurs while reading the specified file
     *
     * @throws          FailedLoginException in case if the authorization on the server has been failed
     *                  e.g. a wrong server login/password has/have been entered
     * */
    public static void sendStopServerMessage(@NotNull File serverPropertiesFile) throws IOException, FailedLoginException {
        if (!arePropertiesValid(serverPropertiesFile)) {
            throw new InvalidPropertiesFormatException("The properties file are not valid");
        }
        Properties properties = new Properties();
        properties.loadFromXML(new BufferedInputStream(new FileInputStream(serverPropertiesFile)));
        sendStopServerMessage(properties);
    }

    /**
     *  This method sends a {@code Message} of status {@code MessageStatus.AUTH} not specifying any additional parameters
     * Thus
     * */
    private static Message sendAndWait(int port, int timeout) throws SocketTimeoutException {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1...65535, but found "
                    .concat(String.valueOf(port)));
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be greater than 0, but found "
                    .concat(String.valueOf(timeout)));
        }
        try (Socket socket = new Socket("localhost", port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(timeout);
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
                Marshaller marshaller = jaxbContext.createMarshaller();
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                StringWriter stringWriter = new StringWriter();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                marshaller.marshal(new Message(MessageStatus.AUTH), stringWriter);
                out.writeUTF(stringWriter.toString());
                Message response = (Message) unmarshaller.unmarshal(new StringReader(in.readUTF()));
                if (MessageStatus.ERROR.equals(response.getStatus())
                        || MessageStatus.DENIED.equals(response.getStatus())) {
                    LOGGER.trace("Received expected answer ".concat(response.toString()));
                } else {
                    LOGGER.warn(buildMessage("Answer has been received but the status is",
                            response.getStatus(), ". Expected either", MessageStatus.ERROR, "or", MessageStatus.DENIED));
                }
                return response;
            } catch (JAXBException e) {
                LOGGER.error(buildMessage("Unknown JAXBException:", e.getLocalizedMessage()));
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *  The method {@code hasAccountBeenRegistered} informs whether there is an account on the server
     * specified by the {@code serverProperties} with this {@code id}
     *
     * @return          {@code true} if and only if the properties being passed are valid and there is a registered
     *                  account having such login name on the server
     * */
    public static boolean hasAccountBeenRegistered(Properties serverProperties, int id) {
        if (!arePropertiesValid(serverProperties)) {
            LOGGER.error("Properties are not valid");
            return false;
        }
        File clientsDir = new File(serverProperties.getProperty("clientsDir"));
        File clientDir = new File(clientsDir, String.valueOf(id));
        File clientXml = new File(clientDir, String.valueOf(id).concat(".xml"));
        return clientDir.isDirectory() && clientXml.isFile();
    }

    public static void saveRooms(Server server) {
        if (server == null || server.getOnlineRooms() == null) {
            String errorMessage = (server == null ? "A server" : "A set of online rooms").concat(" has not been set");
            LOGGER.error(errorMessage);
            throw new NullPointerException(errorMessage);
        }
        synchronized (server.getOnlineRooms().safe()) {
            for (Map.Entry<Integer, Room> entry : server.getOnlineRooms().safe().entrySet()) {
                if (entry.getValue().getServer() != null && !entry.getValue().save()) {
                    LOGGER.error(buildMessage("Room id", entry.getValue().getRoomId(), "has not been saved"));
                }
            }
        }
    }

    public static void saveClients(Server server) {
        if (server == null || server.getOnlineClients() == null) {
            String errorMessage = (server == null ? "A server" : "A set of online clients").concat(" has not been set");
            LOGGER.error(errorMessage);
            throw new NullPointerException(errorMessage);
        }
        synchronized (server.getOnlineClients().safe()) {
            for (Map.Entry<Integer, ClientListener> entry : server.getOnlineClients().safe().entrySet()) {
                if (entry.getValue().getClient() != null && !entry.getValue().getClient().save()) {
                    LOGGER.error(buildMessage("Client id", entry.getValue().getClient().getClientId(),
                            "has not been saved"));
                }
            }
        }
    }

    /**
     *  This method restarts the server specified by the passe {@code serverConfiguration}.
     * */
    public static boolean restartServer(Properties serverConfiguration) throws InvalidPropertiesFormatException {
        if (!arePropertiesValid(serverConfiguration)) {
            String errorString = "The specified properties are not valid. Restart operation aborted";
            LOGGER.warn(errorString);
            throw new InvalidPropertiesFormatException("The specified properties are not valid. Restart operation aborted");
        }
        if (!isServerLaunched(serverConfiguration)) {
            String errorString = "The server is not launched yet";
            LOGGER.warn(errorString);
            throw new IllegalStateException(errorString);
        }
        try (Socket socket = new Socket("localhost", Integer.parseInt(serverConfiguration.getProperty("port")));
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream())) {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            StringWriter stringWriter = new StringWriter();
            Message stopMessage = new Message(MessageStatus.STOP_SERVER)
                    .setLogin(serverConfiguration.getProperty("server_login"))
                    .setPassword(serverConfiguration.getProperty("server_password"));
            marshaller.marshal(stopMessage, stringWriter);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            socket.setSoTimeout(15000);
            dataOutputStream.writeUTF(stringWriter.toString());
            try {
                String responseXmlMessage = dataInputStream.readUTF();
                Message response = (Message) unmarshaller.unmarshal(new StringReader(responseXmlMessage));
                if (!MessageStatus.ACCEPTED.equals(response.getStatus())) {
                    LOGGER.warn(buildMessage("Response from the server (", socket.getRemoteSocketAddress(),
                            ") has been received, but the status is not of expected (", MessageStatus.ACCEPTED ,
                            ") status. Found" ,response.getStatus(), ".Operation aborted"));
                    return false;
                } else {
                    startServer(serverConfiguration);
                    try {
                        LOGGER.trace("Waiting for the server will start");
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        LOGGER.error(buildMessage(e.getClass().getName(), ':', e.getLocalizedMessage()));
                    }
                    return isServerLaunched(serverConfiguration);
                }
            } catch (SocketTimeoutException e) {
                LOGGER.warn("Timeout exceeded. Restart operation has not been completed properly");
                return false;
            }
        } catch (IOException | JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }
}