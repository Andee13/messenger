package server;

import com.sun.org.apache.xml.internal.security.algorithms.MessageDigestAlgorithm;
import common.message.Message;
import common.message.status.MessageStatus;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.security.sasl.AuthenticationException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.crypto.Data;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.ConnectException;
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

    public void setOnlineRooms(@NotNull Map<Integer, Room> onlineRooms) {
        this.onlineRooms = FXCollections.observableMap(onlineRooms);
    }

    protected Server(@NotNull Properties serverConfig) {
        config = serverConfig;
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