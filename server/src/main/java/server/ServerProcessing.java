package server;

import common.message.Message;
import common.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.FailedLoginException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.*;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

/**
 *  This class contains methods which operates with an instance of {@code Server}
 *  e.g. starts a server, stops it or restarts
 *
 * @see Server
 * */
public class ServerProcessing {

    private static final Logger LOGGER = Logger.getLogger("Server");
    private static Properties defaultProperties;

    /**
     *   If the server is being launched without parameters, it is thought
     * that you want to start it from the current folder.
     * Correct root folder structure is expected
     *
     *  In case, if the structure is not kept, the necessary folders and files will be created,
     * server will stop in order you are able to set the configurations.
     *
     * @param           args server start directives
     * */
    public static void main(String[] args) throws IOException {
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
                try {
                    stopServer(serverProperiesFile);
                } catch (Exception e) {
                    LOGGER.error(e.getLocalizedMessage());
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
     * */
    public static boolean arePropertiesValid(@NotNull Properties properties) {
        if (properties == null) {
            return false;
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
     * */
    public static boolean arePropertiesValid(@NotNull File propertyFile) {
        if (propertyFile == null) {
            return false;
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
            // a port number on which the server will be started
            properties.setProperty("port", "5940");
            // a server
            properties.setProperty("server_login", "God");
            properties.setProperty("server_password","change_me");
            // a path to the folder where clients' data will be stored
            properties.setProperty("clientsDir", new StringBuilder("change")
                    .append(File.separatorChar).append("the")
                    .append(File.separatorChar).append("clients")
                    .append(File.separatorChar).append("folder")
                    .append(File.separatorChar).append("path")
                    .toString()
            );
            // a path to the folder where the rooms' data will be stored
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
     * @throws          IOException if specified file either is {@code null} either it does not fit the data
     *                              that it stores does not fits the server configuration pattern demands or
     *                              it does not exist e.g. it may be {@code FileNotFoundException},
     *                              {@code InvalidPropertiesFormatException} or {@code IOException}
     * */
    private static void startServer(@NotNull File serverPropertiesFile) throws IOException {
        if(!arePropertiesValid(serverPropertiesFile)) {
            throw new InvalidPropertiesFormatException("Invalid server properties file");
        }
        Properties serverProperties = new Properties();
        serverProperties.load(new BufferedInputStream(new FileInputStream(serverPropertiesFile)));
        startServer(serverProperties);
    }

    /**
     * The method {@code startServer} starts the server denoted by the specified {@code serverProperties}
     *
     * @throws          IOException if an I/O error occurs
     *
     * @exception       IllegalStateException if the server denoted by the specified {@code serverProperties}
     *                  has already been launched or the port set in the {@code serverProperties} is taken
     * */
    private static void startServer(@NotNull Properties serverProperties) throws IOException{
        if(!arePropertiesValid(serverProperties)) {
            throw new IOException("The server properties are not valid");
        }

        Server server = new Server(serverProperties);
        server.start();
        LOGGER.info(new StringBuilder("Server thread status: ").append(server.getState()).toString());
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
        try {
            serverProperties.load(new BufferedInputStream(new FileInputStream(serverPropertiesFile)));
            return isServerLaunched(serverProperties);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     *  The method {@code stopServer} stops the server denoted by the specified {@code serverProperties}
     *
     * @param           serverProperties a set of server properties
     *
     * @throws          IOException if the {@code serverProperties} are invalid
     *
     * @exception       IllegalStateException if the server denoted by the specified properties
     *                                        has not been launched on this {@code localhost} yet
     *
     * @exception       NullPointerException if the {@code serverProperties} are {@code null}
     *
     * @exception       FailedLoginException in case if failed to authorize on the server
     *                  i.e. the response message status is not {@code MessageStatus.ACCEPTED}
     * */
    public static void stopServer(@NotNull Properties serverProperties) throws IOException, FailedLoginException {
        if (serverProperties == null) {
            throw new NullPointerException("The serverProperties must not be null");
        }
        if (!arePropertiesValid(serverProperties)) {
            throw new IOException("The server properties are not valid");
        }
        Message loginMessage = new Message(MessageStatus.AUTH).setLogin(serverProperties.getProperty("server_login"))
                .setPassword(serverProperties.getProperty("server_password"));
        Message stopRequestMessage = new Message(MessageStatus.STOP_SERVER)
                .setLogin(serverProperties.getProperty("server_login"))
                .setPassword(serverProperties.getProperty("server_password"));
        Socket socket = new Socket("localhost", Integer.parseInt(serverProperties.getProperty("port")));
        Message responseMessage = sendAndWait(loginMessage, socket, 30);
        if (!MessageStatus.ACCEPTED.equals(responseMessage.getStatus())) {
            StringBuilder failedAuthInfo = new StringBuilder("Unable to authorize on the server ")
                    .append(socket.getRemoteSocketAddress()).append("\nResponse status:")
                    .append(responseMessage.getStatus());
            if (responseMessage.getText() != null && !responseMessage.getText().isEmpty()) {
                failedAuthInfo.append(responseMessage.getText());
            }
            throw new FailedLoginException(failedAuthInfo.toString());
        }
        responseMessage = sendAndWait(stopRequestMessage, socket, 30);
        if (!MessageStatus.ACCEPTED.equals(responseMessage.getStatus())) {
            StringBuilder failedStopInfo = new StringBuilder("Unable to stop the server. Response status: ")
                    .append(responseMessage.getStatus());
            if (responseMessage.getText() != null && !responseMessage.getText().isEmpty()) {
                failedStopInfo.append(responseMessage.getText());
            }
            throw new RuntimeException(failedStopInfo.toString());
        }
    }

    /**
     *  The method {@code stopServer} is just an interagent who unpacks server properties from the specified file
     * and invokes {@code stopServer(Properties serverProperties)}
     *
     * @param           serverPropertiesFile the file which stores server properties
     *
     * @throws          IOException in case if {@code serverPropertiesFile} does not contains valid server configuration,
     *                  if an I/O error occurs while reading the specified file
     *
     * @throws          FailedLoginException in case if the authorization on the server has been failed
     *                  e.g. a wrong server login/password has/have been entered
     * */
    public static void stopServer(@NotNull File serverPropertiesFile) throws IOException, FailedLoginException {
        if (!arePropertiesValid(serverPropertiesFile)) {
            throw new InvalidPropertiesFormatException("The properties file are not valid");
        }
        Properties properties = new Properties();
        properties.load(new BufferedInputStream(new FileInputStream(serverPropertiesFile)));
        stopServer(properties);
    }

    /**
     *  The method {@code sendAndWait} sends the specified {@code message} and waits for response
     * for {@code timeout} seconds. If no reply was received for all the time, then {@code ConnectException}
     * will be thrown.
     *  This method was created to check the connection to server lcunched on the {@code socket}. It is supposed
     * that there is an opened socket on the another end and it is listening to connections.
     *
     * @param           message the message to be sent
     * @param           socket the socket that will be used to send the message via {@code socket.getOutputStream()}
     * @param           timeout the time period (in seconds) during which a response will be being waited
     *
     * @exception       ConnectException in case if no response has been got
     * @exception       NullPointerException if {@code message} or {@code socket} is {@code null}
     * @exception       IllegalArgumentException if {@code timeout} is less than 0
     *
     * @throws          IOException if an I/O error occurs
     *
     * @return          an instance of {@code Message} that has been received from the server
     * */
    private static Message sendAndWait(Message message, Socket socket, int timeout) throws IOException {
        if (message == null) {
            throw new NullPointerException("Message must not be null");
        }
        if (socket == null) {
            throw new NullPointerException("Socket must not be null");
        }
        if (timeout < 0) {
            throw new IllegalArgumentException(new StringBuilder("Timeout must be a positive number:")
                    .append(timeout).toString());
        }
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
        DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter stringWriter = new StringWriter();
            marshaller.marshal(message, stringWriter);
            dataOutputStream.writeUTF(stringWriter.toString());
            dataOutputStream.flush();
            long sendingRequestTime = System.currentTimeMillis();
            boolean wasResponse = false;
            String response = null;
            while(System.currentTimeMillis() - sendingRequestTime < timeout * 1000 && !wasResponse) {
                if(dataInputStream.available() != 0) {
                    response = dataInputStream.readUTF();
                    wasResponse = true;
                }
            }
            if (!wasResponse) {
                throw new ConnectException("Response timeout");
            }
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            return (Message) unmarshaller.unmarshal(new StringReader(response));
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
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
    public static boolean hasAccountBeenRegistered(Properties serverProperties, int id) throws InvalidPropertiesFormatException {
        if (!arePropertiesValid(serverProperties)) {
            throw new InvalidPropertiesFormatException("Properties are not valid");
        }
        File clientsDir = new File(serverProperties.getProperty("clientsDir"));
        File clientDir = new File(clientsDir, String.valueOf(id));
        File clientXml = new File(clientDir, String.valueOf(id).concat(".xml"));
        return clientDir.isDirectory() && clientXml.isFile();
    }
}