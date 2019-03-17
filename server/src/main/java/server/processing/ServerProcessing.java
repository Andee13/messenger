package server.processing;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jetbrains.annotations.NotNull;
import server.InvocationMode;
import server.Server;
import server.room.Room;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.net.*;
import java.time.format.DateTimeFormatter;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;
import java.util.Scanner;

import static common.Utils.buildMessage;
import static server.processing.PropertiesProcessing.arePropertiesValid;

/**
 *  This class contains methods which operates with an instance of {@code Server}
 *  e.g. starts a server, stops it or restarts
 *
 * @see Server
 * */
public class ServerProcessing {
    private static volatile Logger LOGGER;
    static Properties defaultProperties;
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    public static final int MESSAGE_HISTORY_DIMENSION = 100;
    static final File currentFolder;

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }
    /*
    *   Detecting the current folder
    * */
    static {
        try {
            currentFolder = new File(ServerProcessing.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile();
            LoggersProcessing.setDefaultLoggersFiles();
            PropertyConfigurator.configure(ServerProcessing.class.getResourceAsStream("../../log4j.properties"));
            LOGGER = Logger.getLogger("ServerProcessing");
            PropertiesProcessing.setLogger(Logger.getLogger(PropertiesProcessing.class.getSimpleName()));
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(buildMessage("Current folder detected:", currentFolder.getAbsolutePath()));
            }
        } catch (URISyntaxException e) {
            System.err.println(buildMessage(e.getClass().getName(), "occurred:", e.getMessage()));
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

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
    public static void main(@SuppressWarnings("ParameterCanBeLocal") String[] args) throws IOException {

        File serverProperiesFile;
        System.out.println("Hello, please, enter one of the following commands:");
        printCommands();
        args = new Scanner(System.in).nextLine().split(" ");
        InvocationMode invocationMode;
        try {
            invocationMode = getInvocationMode(args);
        } catch (IOException e) {
            printCommands();
            return;
        }
        try {
            serverProperiesFile = new File(args[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            serverProperiesFile = new File(currentFolder, "serverConfig.xml");
        }
        if (LOGGER.isEnabledFor(Level.TRACE)) {
            LOGGER.trace(buildMessage("Current serverConfig.xml is:", serverProperiesFile.getAbsolutePath()));
        }
        Properties serverProperties;
        switch (invocationMode) {
            case START:
                try {
                    serverProperties = PropertiesProcessing.loadPropertiesFromFile(serverProperiesFile);
                    startServer(serverProperties);
                } catch (IOException e) {
                    if (LOGGER.isEnabledFor(Level.ERROR)) {
                        LOGGER.error(e.getLocalizedMessage());
                    }
                    return;
                }
                break;
            case STOP:
                try {
                    sendStopServerMessage(serverProperiesFile);
                } catch (Exception e) {
                    if (LOGGER.isEnabledFor(Level.ERROR)) {
                        LOGGER.error(e.getLocalizedMessage());
                    }
                }
                break;
            case RESTART:
                serverProperties = PropertiesProcessing.loadPropertiesFromFile(serverProperiesFile);
                sendRestartMessage(serverProperties);
                break;
            case CREATE_DEFAULT_SERVER:
                try {
                    createDefaultRootStructure(new File(args[1]));
                } catch (ArrayIndexOutOfBoundsException e) {
                    createDefaultRootStructure(currentFolder);
                    return;
                } catch (IllegalArgumentException e) {
                    if (LOGGER.isEnabledFor(Level.ERROR)) {
                        LOGGER.error(buildMessage("Invalid folder path as root directory for the server"
                                , new File(args[1]).getAbsolutePath()));
                    }
                }
                break;
            case BAN:
                try {
                    serverProperties = new Properties();
                    serverProperties.loadFromXML(new FileInputStream(serverProperiesFile));
                    ClientProcessing.clientBan(serverProperties, args[2], true, Integer.parseInt(args[3]));
                } catch (IndexOutOfBoundsException e) {
                    LOGGER.info("Not all arguments are specified. Please, check the input");
                    printCommands();
                } catch (NumberFormatException e) {
                    LOGGER.error("Wrong number of hours entered : ".concat(args[3]));
                }
                break;
            case UNBAN:
                try {
                    serverProperties = new Properties();
                    serverProperties.loadFromXML(new FileInputStream(serverProperiesFile));
                    ClientProcessing.clientBan(serverProperties, args[2], false, 0);
                } catch (IndexOutOfBoundsException e) {
                    LOGGER.error("Not all arguments are specified. Please, check the input");
                    printCommands();
                }
                break;
            case EXIT:
                return;
            default:
                String errorMessage = "Unknown invocation mode: ".concat(String.valueOf(invocationMode));
                LOGGER.error(errorMessage);
                throw new IOException(errorMessage);
        }
    }

    private static void sendRestartMessage(Properties serverConfig) {
        try (Socket socket = new Socket("localhost", Integer.parseInt(serverConfig.getProperty("port")));
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            Message message = new Message(MessageStatus.RESTART_SERVER)
                    .setLogin(serverConfig.getProperty("serverLogin"))
                    .setPassword(serverConfig.getProperty("serverPassword"));
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            StringWriter stringWriter = new StringWriter();
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(message,stringWriter);
            out.writeUTF(stringWriter.toString());
            out.flush();
        } catch (JAXBException | IOException e) {
            LOGGER.error(buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
        }
    }

    private static void printCommands() {
        System.out.println("                                    <---Available commands--->");
        System.out.println("-cds path/to/server/root/folder                 - to create a default server root structure in the specified folder");
        System.out.println("-start path/to/serverConfig.xml                 - to start the server denoted by the configurations");
        System.out.println("-restart path/to/serverConfig.xml               - to restart the server denoted by the configurations");
        System.out.println("-stop path/to/serverConfig.xml                  - to stop the server denoted by the configurations");
        System.out.println("-ban path/to/serverConfig.xml <login> <hours>   - to ban the client on the server denoted by the configurations");
        System.out.println("-unban path/to/serverConfig.xml <login>         - to unban the client on the server denoted by the configurations");
        System.out.println("-exit                                           - to exit the program");

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
                case "-exit":
                    return InvocationMode.EXIT;
                default:
                    String errorMessage = buildMessage("Unknown command:", args[0]);
                    if (LOGGER.isEnabledFor(Level.ERROR)) {
                        LOGGER.error(errorMessage);
                    }
                    throw new IOException(errorMessage);
            }
        }
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
                Properties defaultProperties = PropertiesProcessing.getDefaultProperties();
                defaultProperties.setProperty("roomsDir", roomsDir.getAbsolutePath());
                defaultProperties.setProperty("clientsDir", clientsDir.getAbsolutePath());
                defaultProperties.setProperty("logsDir", logsDir.getAbsolutePath());
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

    static void startServer(Properties serverConfiguration) throws IOException {
        if (!arePropertiesValid(serverConfiguration)) {
            String errorMessage = buildMessage("The passed server configuration file/path is not valid");
            if (LOGGER.isEnabledFor(Level.ERROR)) {
                LOGGER.error(errorMessage);
            }
            throw new InvalidPropertiesFormatException(errorMessage);
        }
        startServer(new File(serverConfiguration.getProperty("serverConfig")));
    }

    /**
     *  The method {@code sendStopServerMessage} stops the server specified by the properties
     * */
    private static void sendStopServerMessage(@NotNull Properties serverProperties) {
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
                    .setPassword(serverProperties.getProperty("serverPassword"))
                    .setLogin(serverProperties.getProperty("serverLogin"));
            socket.setSoTimeout(10000);
            StringWriter stringWriter = new StringWriter();
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(message, stringWriter);
            dataOutputStream.writeUTF(stringWriter.toString());
            dataOutputStream.flush();

            LOGGER.info(buildMessage("The Message of", MessageStatus.STOP_SERVER,
                    "status has been sent to address localhost:", serverProperties.getProperty("port")));
        } catch (SocketException e) {
            LOGGER.info("The server is not launched");
        } catch (IOException | JAXBException e) {
            LOGGER.error(e.getMessage());
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
     * */
    private static void sendStopServerMessage(@NotNull File serverPropertiesFile) throws IOException {
        if (!arePropertiesValid(serverPropertiesFile)) {
            throw new InvalidPropertiesFormatException("The properties file are not valid");
        }
        Properties properties = new Properties();
        properties.loadFromXML(new BufferedInputStream(new FileInputStream(serverPropertiesFile)));
        sendStopServerMessage(properties);
    }
}