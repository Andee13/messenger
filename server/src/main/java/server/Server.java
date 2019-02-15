package server;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@XmlRootElement
public class Server extends Thread {
    private ObservableMap<Integer, ClientListener> clients;
    private ObservableMap<Integer, Room> onlineRooms;
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger("Server");
    private Properties config;
    private static final Set inputKeys;

    public Properties getConfig() {
        return config;
    }

    static {
        inputKeys = new TreeSet<String>();
        inputKeys.addAll(Arrays.asList("start", "stop" , "restart"));
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

    public static void main(String[] args) {
        /*

        defaultProperties.setProperty("clientsDir", new StringBuilder(new File(Server.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath()).getAbsolutePath()).append("clients").toString());
            defaultProperties.setProperty("roomsDir", new StringBuilder(new File(Server.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath()).getAbsolutePath()).append("rooms").toString());

        */


        if (args.length == 0) {
            File rootDir = null;
            try {
                rootDir = new File(Server.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            } catch (URISyntaxException e) {
                LOGGER.fatal(new StringBuilder("The root folder definition has failed:\n")
                        .append(e.getLocalizedMessage()).toString());
            }
            if (isRootStructure(rootDir)) {
                File propertiesFile = new File(rootDir.getAbsolutePath().concat("serverConfig.xml"));
                Properties serverProperties = new Properties();
                if (!arePropertiesValid(propertiesFile)) {
                    LOGGER.error("The property file is not valid. See more in logs");
                    return;
                } else {
                    Server server = new Server(serverProperties);
                    server.run();
                }
            } else {

            }
        } else {
            String inputString;
            StringBuilder stringBuilder = new StringBuilder();
            for (String arg : args) {
                stringBuilder.append(arg).append(' ');
            }
            inputString = stringBuilder.toString().trim();
            // TODO
        }
    }

    /**
     *  The method {@code parseInput} decides what the server has to do depending on passed parameters
     *
     * @param           args is the program input parameters
     * */
    private static void parseInput(@NotNull String [] args) {
        /*  If any input parameters have not been passed it is thought
         * that the serverConfig.xml is in the current folder.
         * */
        if(args.length == 0) {
            File currentFolder = null;
            try {
                currentFolder = new File(new File(Server.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).getAbsolutePath());
            } catch (URISyntaxException e) {
                LOGGER.fatal(e.getLocalizedMessage());
                return;
            }
            File propertiesFile = new File(currentFolder.getAbsolutePath()
                    .concat(File.pathSeparator).concat("serverConfig.xml"));
            if(arePropertiesValid(propertiesFile)) {
                Properties serverConfig = new Properties();
                try {
                    serverConfig.load(new BufferedInputStream(new FileInputStream(propertiesFile)));
                    Server server = new Server(serverConfig);
                    server.run();
                    if (server.getState() == State.RUNNABLE) {
                        LOGGER.info("Server has been successfully launched");
                    } else {
                        LOGGER.warn(new StringBuilder("Please, check the server. Server thread status is ")
                                .append(server.getState()));
                    }
                } catch (IOException e) {
                    LOGGER.fatal(e.getLocalizedMessage());
                }
            } else {
                LOGGER.info("The server has not been started. For more info, please, check logs");
            }
        } else {

        }
    }

    /**
     * The method {@code divideInputParametersOnKeys} divides the input parameters into separate parts e.g.
     * "-login gandalf -password youshallnotpass" will be sorted into
     * login=ivan
     * passowrd=youshallnotpass
     * */
    private static Properties divideInputParametersOnKeys(@NotNull String input) throws IOException {
        input = input.trim();
        Properties executeProperties = new Properties();
        String keysParametersPatternString = "^(((-\\w+) (\\S+))( )*)+$";
        Pattern keysParameterPattern = Pattern.compile(keysParametersPatternString);
        Matcher matcher = keysParameterPattern.matcher(input);
        if (!matcher.matches()) {
            throw new IOException(new StringBuilder("Wrong input: ").append(input).toString());
        }
        String [] splittedInputParameters = input.split("-");
        for(int i = 0; i < splittedInputParameters.length; i++) {
            splittedInputParameters[i] = splittedInputParameters[i].trim();
            String key = splittedInputParameters[i].split(" ")[0];
            String value = splittedInputParameters[i].split(" ")[1];
            /*
            * Checks if the entered key is valid
            * */
            if(!inputKeys.contains(key)) {
                throw new IllegalArgumentException(new StringBuilder("Unknown key: ").append(key).toString());
            }
            if (executeProperties.setProperty(key, value) != null) {
                throw new IOException(new StringBuilder("Duplicating the key ").append(key).toString());
            }

        }

        // TODO
        return null;
    }

    /**
     *  The method {@code arePropertiesValid} checks if the passed abstract path is a valid file.
     * Returns {@code true} if and only if the specified by the abstract path file exists and contains
     * properties about existing clients and rooms directories, {@code false} otherwise.
     *
     * @param           properties a set of properties are to be validated
     * */
    public static boolean arePropertiesValid(Properties properties) {
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
     *  The method creates an instance of {@code Property} and loads the properties
     * from the specified file.
     *
     *  The result is the same as a result of invocation {@code arePropertiesValid()}
     * */
    public static boolean arePropertiesValid(@NotNull File propertyFile) {
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
     * */
    private static boolean isRootStructure(@NotNull File rootDir) {
        File serverConfig = new File(new StringBuffer(rootDir.getAbsolutePath()).append("serverConfig.xml").toString());
        File clientsDir = new File(new StringBuffer(rootDir.getAbsolutePath()).append("clients").toString());
        File roomsDir = new File(new StringBuffer(rootDir.getAbsolutePath()).append("rooms").toString());
        return serverConfig.isFile() && clientsDir.isDirectory() && roomsDir.isDirectory();
    }

    /**
     *  Organizes the necessary server root folder structure described above the {@code ServerConfig.class}
     * The method creates one of more demanded element. If there is not such one, the {@code createRootStructure}
     * creates it. The method does not delete or re-write already existing ones.
     *
     * @param           rootDir a server root folder
     * */
    private static void createRootStructure(@NotNull File rootDir) {
        if (!rootDir.isDirectory()) {
            throw new IllegalArgumentException("rootDir is expected to be an existing folder");
        }
        File serverConfig = new File(new StringBuffer(rootDir.getAbsolutePath()).append("serverConfig.xml").toString());
        File clientsDir = new File(new StringBuffer(rootDir.getAbsolutePath()).append("clients").toString());
        File roomsDir = new File(new StringBuffer(rootDir.getAbsolutePath()).append("rooms").toString());
        if (!serverConfig.isFile()) {
            LOGGER.info(new StringBuilder("Creating the default server configuration file: ")
                    .append(serverConfig.getAbsolutePath()));
            try {
                if(!serverConfig.createNewFile()){
                    throw new RuntimeException(new StringBuilder("Unable to create a default server configuration file: ")
                            .append(serverConfig.getAbsolutePath()).toString());
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
     *  Starts the server for which {@code rootDir} is the root folder
     * */
    private static void startServer(File rootDir) {
        // TODO
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
        LOGGER.info("Stopping the serverProcessing");
        for (Map.Entry<Integer, ClientListener> client : clients.entrySet()) {
            client.getValue().closeClientSession();
        }
        interrupt();
    }

    public void closeClientSession (ClientListener client) throws IOException, JAXBException {
        client.closeClientSession();
    }

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