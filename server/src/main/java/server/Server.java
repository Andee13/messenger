package server;

import common.entities.Saveable;
import common.entities.Shell;
import javafx.collections.FXCollections;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import server.client.ClientListener;
import server.processing.LoggersProcessing;
import server.processing.PropertiesProcessing;
import server.room.Room;
import server.room.RoomProcessing;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.InvalidPropertiesFormatException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static common.Utils.buildMessage;

public class Server extends Thread implements Saveable {
    private volatile Shell<Map<Integer, ClientListener>> onlineClients;
    private volatile Shell<Map<Integer, Room>> onlineRooms;
    private static volatile Logger LOGGER = Logger.getLogger("Server");
    private volatile Properties config;
    private File clientsDir;
    private File serverConfigFile;
    private volatile ServerSocket serverSocket;

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

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
                throw new RuntimeException(
                        buildMessage("Unable to get property \"clientsDir\" from the configuration", config.toString()));
            }
            File clientsDir = new File(clientsDirPath);
            if (!clientsDir.isDirectory()) {
                throw new RuntimeException(
                        buildMessage("Unable to find a folder:", clientsDir.getAbsolutePath()));
            }
            this.clientsDir = clientsDir;
        }
        if (clientsDir.isDirectory()) {
            return clientsDir;
        } else {
            throw new RuntimeException(
                    buildMessage("Unable to find a onlineClients folder", clientsDir.getAbsolutePath()));
        }
    }

    public Shell<Map<Integer, ClientListener>> getOnlineClients() {
        return onlineClients;
    }

    public Shell<Map<Integer, Room>> getOnlineRooms() {
        return onlineRooms;
    }

    /**
     * @param           serverPropertiesFile a file storing server configurations
     *
     * @throws          InvalidPropertiesFormatException if the passed {@code serverPropertiesFile} is not valid
     *                  e.g. is {@code null}, does not contain a property or it is not valid
     * */
    public Server(@NotNull File serverPropertiesFile) throws InvalidPropertiesFormatException {
        onlineClients = new Shell<>();
        onlineRooms = new Shell<>();
        initOnlineClients();
        initOnlineRooms();
        if (!PropertiesProcessing.arePropertiesValid(serverPropertiesFile)) {
            throw new InvalidPropertiesFormatException("Either the specified properties or file are/is invalid");
        }
        config = new Properties();
        try(FileInputStream fileInputStream = new FileInputStream(serverPropertiesFile)) {
            config.loadFromXML(fileInputStream);
            LoggersProcessing.setLoggersFilesSysProperties(config);
            LoggersProcessing.resetLoggers();
            serverConfigFile = serverPropertiesFile;
            onlineClients =
                    new Shell<>(FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>())));
            onlineRooms =
                    new Shell<>(FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>())));
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
        RoomProcessing.loadRoom(this, 0);
    }

    private void initOnlineClients() {
        onlineClients =
                new Shell<>(FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>())));
    }

    private void initOnlineRooms() {
        onlineRooms =
                new Shell<>(FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>())));
    }

    @Override
    public void run() {
        Observer observer = new Observer(this);
        observer.setDaemon(true);
        observer.start();
        LOGGER.info(buildMessage("Observer thread status:", observer.getState()));
        if (!PropertiesProcessing.arePropertiesValid(config)) {
            LOGGER.fatal("Unable to start the server. Server configurations are not valid.");
            return;
        }
        Socket socket;
        try {
            serverSocket = new ServerSocket(Integer.parseInt(config.getProperty("port")));
            while (!isInterrupted() && !serverSocket.isClosed()) {
                try {
                    socket = serverSocket.accept();
                    LOGGER.info(buildMessage("Incoming connection from:", socket.getInetAddress()));
                    ClientListener clientListener = new ClientListener(this, socket);
                    clientListener.start();
                } catch (IOException e) {
                    LOGGER.error(buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
                }
            }
        } catch (IOException e) {
            LOGGER.fatal(buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
        } finally {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                LOGGER.fatal(buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
            }
            interrupt();
        }
    }

    /**
     *  The method {@code save} stores the XML representation of the {@code config} to the {@code serverConfigFile}
     *
     * @return          {@code true} if and only if the {@code config} has been stored to the corresponding file
     *                  and the data in that file has been stored correctly i.e. {@code config} contains the same
     *                  data as {@code serverConfigFile}
     * */
    @Override
    public synchronized boolean save() {
        if (config == null) {
            LOGGER.warn("Saving the server has been failed: undefined server configurations.");
        }
        if (!PropertiesProcessing.arePropertiesValid(config)) {
            LOGGER.warn("Saving the server has been failed: invalid server properties.");
            return false;
        }
        if (serverConfigFile == null) {
            LOGGER.fatal("Saving the server has been failed: server configuration file must not be null");
            return false;
        }
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(serverConfigFile))) {
            config.storeToXML(bos, null);
            synchronized (onlineClients.safe()) {
                for (Map.Entry<Integer, ClientListener> onlineClients : onlineClients.safe().entrySet()) {
                    ClientListener clientListener = onlineClients.getValue();
                    if (clientListener.getClient() != null && !clientListener.getClient().save()) {
                        LOGGER.error(buildMessage("Failed to save the client (id"
                                , clientListener.getClient().getClientId()));
                        return false;
                    }
                }
            }
            synchronized (onlineRooms.safe()) {
                for (Map.Entry<Integer, Room> onlineRooms : onlineRooms.safe().entrySet()) {
                    if (!onlineRooms.getValue().save()) {
                        LOGGER.error(
                                buildMessage("Failed to save the room (id"
                                        , onlineRooms.getValue().getRoomId(), ')'));
                        return false;
                    }
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

    /**
     *  This method saves and stops the clients with their threads. As it has worked all the clients
     * that had been being listened in the {@code onlineClients} set are saved in the corresponding files
     * */
    private void interruptOnlineClientsThreads() {
        synchronized (onlineClients.safe()) {
            for (Map.Entry<Integer, ClientListener> clientListenerEntry : onlineClients.safe().entrySet()) {
                clientListenerEntry.getValue().interrupt();
                LocalDateTime timeOut = LocalDateTime.now().plusSeconds(3);
                while (clientListenerEntry.getValue().getState().equals(State.RUNNABLE)
                        || LocalDateTime.now().isBefore(timeOut)) {
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        if (LOGGER.isEnabledFor(Level.ERROR)) {
                            LOGGER.error(
                                    buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
                        }
                    }
                }
                if (!clientListenerEntry.getValue().isInterrupted()) {
                    LOGGER.error(buildMessage("Failed to interrupt client's (id"
                            , clientListenerEntry.getValue().getClient().getClientId(), ") thread"));
                }
            }
        }
    }

    @Override
    public void interrupt() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        save();
        interruptOnlineClientsThreads();
        super.interrupt();
        while (!isInterrupted()){
            try {
                sleep(100);
            } catch (InterruptedException e) {
                LOGGER.info(buildMessage("The server has stopped. Thread state is", getState()));
                break;
            }
        }
    }
}