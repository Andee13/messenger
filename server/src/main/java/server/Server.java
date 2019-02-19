package server;

import common.Room;
import common.RoomProcessing;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.TreeMap;

@XmlRootElement
public class Server extends Thread {
    private volatile Map<Integer, ClientListener> clients;
    private volatile Map<Integer, Room> onlineRooms;
    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    private static final Logger LOGGER = Logger.getLogger("Server");
    private Properties config;
    private File clientsDir;
    private File roomsDir;

    public Properties getConfig() {
        return config;
    }

    /**
     *  This method returns an instance of {@code File} that represents
     * an abstract path to the folder where clients data is stored.
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
            throw new RuntimeException(new StringBuilder("Unable to find a clients folder ")
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

    public Map<Integer, ClientListener> getClients() {
        return clients;
    }

    public void setClients(ObservableMap<Integer, ClientListener> clients) {
        this.clients = clients;
    }

    public Map<Integer, Room> getOnlineRooms() {
        return onlineRooms;
    }

    public void setOnlineRooms(@NotNull Map<Integer, Room> onlineRooms) {
        this.onlineRooms = FXCollections.observableMap(onlineRooms);
    }

    protected Server(@NotNull Properties serverConfig) {
        config = serverConfig;
        clients = FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>()));
        onlineRooms = FXCollections.synchronizedObservableMap(FXCollections.observableMap(new TreeMap<>()));
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

    /**
     *  The method {@code clodseClientSession} just invokes the analogous method of the specified {@code ClientListener}
     * */
    public void closeClientSession (@NotNull ClientListener client) throws IOException {
        client.closeClientSession();
    }

    /**
     *  The method {@code loadRoomToOnlineRooms} loads the specified room's data representing it by an instance of {@code Room}
     *
     * @param           id the id of the room to be load
     *
     * @exception       IllegalStateException if server configuration have not been set
     *
     * @exception       NoSuchElementException if there is not such file in the rooms folder
     *
     * */
    public void loadRoomToOnlineRooms(int id) throws IOException{
        onlineRooms.put(id, RoomProcessing.getRoom(config, id));
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