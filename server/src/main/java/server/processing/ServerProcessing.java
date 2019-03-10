package server.processing;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import server.InvocationMode;
import server.Server;
import server.room.Room;
import sun.rmi.runtime.Log;

import javax.security.auth.login.FailedLoginException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.*;
import java.time.format.DateTimeFormatter;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import static common.Utils.buildMessage;

/**
 *  This class contains methods which operates with an instance of {@code Server}
 *  e.g. starts a server, stops it or restarts
 *
 * @see Server
 * */
public class ServerProcessing {

    public static final Logger LOGGER = Logger.getLogger("ServerProcessing");
    public static Properties defaultProperties;
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
                    ClientPocessing.clientBan(serverProperties, args[2], true, Integer.parseInt(args[3]));
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
                    ClientPocessing.clientBan(serverProperties, args[2], false, 0);
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
     * The method {@code startServer} starts the server denoted by the specified {@code serverPropertiesFile}
     *
     * @throws          IOException if an I/O error occurs
     *
     * @exception       IllegalStateException if the server denoted by the specified {@code serverPropertiesFile}
     *                  has already been launched or the port set in the {@code serverPropertiesFile} is taken
     * */
    private static void startServer(@NotNull File serverPropertiesFile) throws IOException {
        if (!PropertiesProcessing.arePropertiesValid(serverPropertiesFile)) {
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
     *  The method {@code sendStopServerMessage} stops the server specified by the properties
     * */
    public static void sendStopServerMessage(@NotNull Properties serverProperties) {
        if (!PropertiesProcessing.arePropertiesValid(serverProperties)) {
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
        if (!PropertiesProcessing.arePropertiesValid(serverPropertiesFile)) {
            throw new InvalidPropertiesFormatException("The properties file are not valid");
        }
        Properties properties = new Properties();
        properties.loadFromXML(new BufferedInputStream(new FileInputStream(serverPropertiesFile)));
        sendStopServerMessage(properties);
    }

    /**
     *  This method restarts the server specified by the passe {@code serverConfiguration}.
     * */
    public static void restartServer(Properties serverConfiguration) throws IOException {
        if (!PropertiesProcessing.arePropertiesValid(serverConfiguration)) {
            String errorString = "The specified properties are not valid. Restart operation aborted";
            LOGGER.warn(errorString);
            throw new InvalidPropertiesFormatException("The specified properties are not valid. Restart operation aborted");
        }
        if (!isServerLaunched(serverConfiguration)) {
            String errorString = "The server is not launched";
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
                } else {
                    try {
                        LOGGER.trace("Waiting the server has stopped");
                        Thread.sleep(4000);
                    } catch (InterruptedException e) {
                        LOGGER.error(buildMessage(e.getClass().getName(), ':', e.getLocalizedMessage()));
                    }
                    startServer(serverConfiguration);
                    try {
                        LOGGER.trace("Waiting for the server will start");
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        LOGGER.error(buildMessage(e.getClass().getName(), ':', e.getLocalizedMessage()));
                    }
                    LOGGER.info(buildMessage("Attempt to connect the server",
                            isServerLaunched(serverConfiguration) ? "succeed" : "failed"));
                }
            } catch (SocketTimeoutException e) {
                LOGGER.warn(buildMessage("Timeout exceeded (response from the server has not been received)",
                        "Restart operation has not been completed properly"));
                return;
            }
        } catch (IOException | JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    public static void restartServer(File serverConfiguration) throws IOException {
        Properties serverProperties = new Properties();
        try (FileInputStream fis = new FileInputStream(serverConfiguration)) {
            serverProperties.loadFromXML(fis);
            restartServer(serverProperties);
        } catch (IOException e) {
            LOGGER.error(buildMessage(e.getClass().getName(), "occurred", e.getLocalizedMessage()));
            throw e;
        }
    }
}