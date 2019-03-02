package server;

import common.Saveable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import server.client.ClientListener;
import server.room.Room;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

@XmlRootElement
public class Server extends Thread implements Saveable {
    private volatile Map<Integer, ClientListener> onlineClients;
    private volatile Map<Integer, Room> onlineRooms;
    private static final Logger LOGGER = Logger.getLogger("Server");
    private volatile Properties config;
    private File clientsDir;
    private File roomsDir;
    private File serverConfigFile;

    public Properties getConfig() {
        return config;
    }

    /**
     *  This method returns an instance of {@code File} that represents
     * an abstract path to the folder where onlineClients data is stored.
     *  Every time once is invoked it checks whether the folder still exists,
     * thus if it has returned an instance of {@code File} it is guaranteed
     * that the result is an existing folder
     *
     * @return          users data storage folder
     *
     * @exception       RuntimeException in case if the folder was removed while
     *                  the server was working
     * */
    public File getClientsDir() {
        if (clientsDir == null) {
            String clientsDirPath = config.getProperty("clientsDir");
            if (clientsDirPath == null) {
                throw new RuntimeException(new StringBuilder("Unable to get property \"clientsDir\" from the configuration")
                        .append(config.toString()).toString());
            }
            File clientsDir = new File(clientsDirPath);
            if (!clientsDir.isDirectory()) {
                throw new RuntimeException(new StringBuilder("Unable to find a folder: ")
                        .append(clientsDir.getAbsolutePath()).toString());
            }
            this.clientsDir = clientsDir;
        }
        if (clientsDir.isDirectory()) {
            return clientsDir;
        } else {
            throw new RuntimeException(new StringBuilder("Unable to find a onlineClients folder ")
                    .append(clientsDir.getAbsolutePath()).toString());
        }
    }

    /**
     *  This method returns an instance of {@code File} that represents an abstract path
     * to the folder where data of the server chat rooms is stored.
     *  Every time it is invoked it checks whether the folder still exists,
     * thus once it has returned an instance of {@code File} it is guaranteed
     * that the result is an existing folder
     *
     * @return          rooms data storage folder
     *
     * @exception       RuntimeException in case if the folder was removed while
     *                  the server was working
     * */
    public File getRoomsDir() {
        if (roomsDir == null) {
            String roomsDirPath = config.getProperty("roomsDir");
            if (roomsDir == null) {
                throw new RuntimeException(new StringBuilder("Unable to get property \"roomsDir\" from the configuration")
                        .append(config.toString()).toString());
            }
            File roomsDir = new File(roomsDirPath);
            if (!roomsDir.isDirectory()) {
                throw new RuntimeException(new StringBuilder("Unable to find a folder: ")
                        .append(roomsDir.getAbsolutePath()).toString());
            }
            this.roomsDir = roomsDir;
        }
        if (roomsDir.isDirectory()) {
            return roomsDir;
        } else {
            throw new RuntimeException(new StringBuilder("Unable to find a rooms folder ")
                    .append(roomsDir.getAbsolutePath()).toString());
        }
    }

    public Map<Integer, ClientListener> getOnlineClients() {
        return onlineClients;
    }

    public void setOnlineClients(ObservableMap<Integer, ClientListener> onlineClients) {
        this.onlineClients = onlineClients;
    }

    public Map<Integer, Room> getOnlineRooms() {
        return onlineRooms;
    }

    public void setOnlineRooms(@NotNull Map<Integer, Room> onlineRooms) {
        this.onlineRooms = FXCollections.observableMap(onlineRooms);
    }

    /**
     * @param           serverPropertiesFile a file storing server configurations
     *
     * @throws          InvalidPropertiesFormatException if the passed {@code serverPropertiesFile} is not valid
     *                  e.g. is {@code null}, does not contain a property or it is not valid
     * */
    public Server(@NotNull File serverPropertiesFile) throws InvalidPropertiesFormatException {
        initOnlineClients();
        initOnlineRooms();
        if (!ServerProcessing.arePropertiesValid(serverPropertiesFile)) {
            throw new InvalidPropertiesFormatException("Either the specified properties or file are/is invalid");
        }
        config = new Properties();
        try(FileInputStream fileInputStream = new FileInputStream(serverPropertiesFile)) {
            config.loadFromXML(fileInputStream);
            serverConfigFile = serverPropertiesFile;
            onlineClients = FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>()));
            onlineRooms = FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>()));
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    private void initOnlineClients() {
        onlineClients = FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>()));
    }

    private void initOnlineRooms() {
        onlineRooms = FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>()));
    }

    @Override
    public void run() {
        if (!ServerProcessing.arePropertiesValid(config)) {
            LOGGER.fatal("Unable to start the server. Server configurations are not valid.");
            return;
        }
        Socket socket;
        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(config.getProperty("port")))) {
            while (!isInterrupted()) {
                try {
                    socket = serverSocket.accept();
                    LOGGER.info(new StringBuilder("Incoming connection from: ")
                            .append(socket.getInetAddress()).toString());
                    ClientListener clientListener = new ClientListener(this, socket);
                    clientListener.start();
                } catch (IOException e) {
                    LOGGER.error(e.getLocalizedMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.fatal("Error occurred while starting the server: ".concat(e.getLocalizedMessage()));
        } finally {
            interrupt();
        }
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

    /**
     *  The method {@code save} stores the XML representation of the {@code config} to the {@code serverConfigFile}
     *
     * @return          {@code true} if and only if the {@code config} has been stored to the corresponding file
     *                  and the data in that file has been stored correctly i.e. {@code config} contains the same
     *                  data as {@code serverConfigFile}
     * */
    @Override
    public boolean save() {
        if (config == null) {
            LOGGER.warn("Saving the server has been failed: undefined server configurations.");
        }
        if (!ServerProcessing.arePropertiesValid(config)) {
            LOGGER.warn("Saving the server has been failed: invalid server properties.");
            return false;
        }
        if (serverConfigFile == null) {
            LOGGER.fatal("Saving the server has been failed: server configuration file must not be null");
            return false;
        }
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(serverConfigFile))) {
            config.storeToXML(bos, null);
            for (Map.Entry<Integer, ClientListener> onlineClients : onlineClients.entrySet()) {
                ClientListener clientListener = onlineClients.getValue();
                if (clientListener.getClient() != null && !clientListener.getClient().save()) {
                    LOGGER.error(new StringBuilder("Failed to save the client id ")
                            .append(clientListener.getClient().getClientId()));
                    return false;
                }
            }
            for (Map.Entry<Integer, Room> onlineRooms : onlineRooms.entrySet()) {
                if (!onlineRooms.getValue().save()) {
                    LOGGER.error(new StringBuilder("Failed to save the room id ")
                            .append(onlineRooms.getValue().getRoomId()).toString());
                    return false;
                }
            }
            return true;
        } catch (FileNotFoundException e) {
            LOGGER.error("Unable to find a server configuration file ".concat(serverConfigFile.getAbsolutePath()));
            return false;
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
    }
    private boolean interruptOnlineClientsThreads() {
        for (Map.Entry<Integer, ClientListener> clientListenerEntry : onlineClients.entrySet()) {
            clientListenerEntry.getValue().interrupt();
            if (!clientListenerEntry.getValue().isInterrupted()) {
                LOGGER.error(new StringBuilder("Failed to interrupt client's (id ")
                        .append(clientListenerEntry.getValue().getClient().getClientId()).append(") thread"));
                return false;
            }
        }
        return true;
    }
    @Override
    public void interrupt() {
        save();
        interruptOnlineClientsThreads();
        super.interrupt();
        System.exit(1);
    }
}