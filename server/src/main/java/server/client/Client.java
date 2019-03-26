package server.client;

import common.entities.Saveable;
import common.entities.Shell;
import common.entities.message.Message;
import javafx.collections.FXCollections;
import org.apache.log4j.Logger;
import server.Server;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static common.Utils.buildMessage;

@SuppressWarnings("CanBeFinal")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="client")
public class Client implements Saveable {
    private int clientId;
    @XmlJavaTypeAdapter(RoomsWrapperAdapter.class)
    private Shell<Set<Integer>> rooms;
    @XmlJavaTypeAdapter(FriendsWrapperAdapter.class)
    private Shell<Set<Integer>> friends;
    private String login;
    private String password;
    @SuppressWarnings("unused")
    private boolean isAdmin;
    private boolean baned;
    @XmlJavaTypeAdapter(value = Message.LocalDateTimeAdapter.class)
    private LocalDateTime isBannedUntill;
    @XmlTransient
    private Server server;

    public LocalDateTime getIsBannedUntill() {
        return isBannedUntill;
    }

    public void setIsBannedUntil(LocalDateTime isBannedUntill) {
        this.isBannedUntill = isBannedUntill;
    }

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

    public boolean isBaned() {
        return baned;
    }

    public void setBaned(boolean baned) {
        this.baned = baned;
    }

    private static volatile Logger LOGGER = Logger.getLogger(Client.class.getSimpleName());

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Client() {
        friends = new Shell<>(FXCollections.synchronizedObservableSet(FXCollections.observableSet(new HashSet<>())));
        rooms = new Shell<>(FXCollections.synchronizedObservableSet(FXCollections.observableSet(new HashSet<>())));
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public Shell<Set<Integer>> getRooms() {
        return rooms;
    }

    String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     *  The {@code clientId} of a client is considered as it's {@code hashCode} value
     * because there must not be two clients with equal {@code clientId}
     * */
    @Override
    public int hashCode() {
        return clientId;
    }

    @Override
    public boolean equals(Object object){
        if (!(object instanceof Client)) {
            return false;
        }
        Client that = (Client) object;
        if (clientId != that.clientId) {
            return false;
        }
        Properties thisProperties = new Properties();
        Properties thatProperties = new Properties();

        thisProperties.setProperty("login", login);
        thatProperties.setProperty("login", that.getLogin());

        thisProperties.setProperty("password", password);
        thatProperties.setProperty("password", that.getPassword());

        thisProperties.setProperty("isAdmin", String.valueOf(this.isAdmin));
        thatProperties.setProperty("isAdmin", String.valueOf(that.isAdmin));

        return thisProperties.equals(thatProperties)
                && rooms.safe().equals(that.getRooms().safe())
                && friends.safe().equals(that.friends.safe());

    }

    /**
     *  The method {@code save} in {@code Client} class is used only for saving during a session closing
     * */
    @Override
    public synchronized boolean save() {
        if (server == null) {
            throw new IllegalStateException("The server has not been set");
        }
        if (login == null) {
            LOGGER.warn("The client saving has been failed: a login has not been set");
            return false;
        }
        if (password == null) {
            LOGGER.warn("The client saving has been failed: a password has not been set");
            return false;
        }
        if (clientId == 0) {
            LOGGER.warn("The client saving has been failed: an id has not been set");
            return false;
        }
        File clientsDir = server.getClientsDir();
        if (!clientsDir.isDirectory()) {
            LOGGER.warn(buildMessage("The client saving has been failed: could not create a directory ",
                    clientsDir.getAbsolutePath()));
            return false;
        }
        File clientDir = new File(clientsDir, String.valueOf(login.hashCode()));
        if (!clientDir.isDirectory()) {
            if (!clientDir.mkdir()) {
                LOGGER.warn(buildMessage("The client saving has been failed: could not create a directory ",
                        clientDir.getAbsolutePath()));
                return false;
            }
        }
        File clientFile = new File(clientDir, String.valueOf(login.hashCode()).concat(".xml"));
        if (!clientDir.isDirectory()) {
            try {
                if (!clientDir.createNewFile()) {
                    LOGGER.warn(buildMessage("The client saving has been failed: could not create a directory ",
                            clientDir.getAbsolutePath()));
                }
            } catch (IOException e) {
                LOGGER.warn(e.getLocalizedMessage());
                return false;
            }
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(this, clientFile);
            return true;
        } catch (JAXBException e) {
            e.printStackTrace();
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class RoomsWrapper {
        @XmlElement(name="roomId")
        private Set<Integer> rooms;

        public RoomsWrapper() {
            rooms = FXCollections.synchronizedObservableSet(FXCollections.observableSet(new HashSet<>()));
        }

        public RoomsWrapper(Set<Integer> wrappedSet){
            rooms = wrappedSet;
        }

        public Set<Integer> getRooms() {
            return rooms;
        }

        public void setRooms(HashSet<Integer> rooms) {
            this.rooms = rooms;
        }
    }

    private static final class RoomsWrapperAdapter extends XmlAdapter<RoomsWrapper, Shell<Set<Integer>>> {
        @Override
        public Shell<Set<Integer>> unmarshal(RoomsWrapper v) {
            return new Shell<>(v.getRooms());
        }
        @Override
        public RoomsWrapper marshal(Shell<Set<Integer>> v) {
            return new RoomsWrapper(v.safe());
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class FriendsWrapper {
        @XmlElement(name="clientId")
        private Set<Integer> rooms;

        public FriendsWrapper() {
            rooms = FXCollections.synchronizedObservableSet(FXCollections.observableSet(new HashSet<>()));
        }

        public FriendsWrapper(Set<Integer> wrappedSet){
            rooms = wrappedSet;
        }

        public Set<Integer> getRooms() {
            return rooms;
        }

        public void setRooms(HashSet<Integer> rooms) {
            this.rooms = rooms;
        }
    }

    private static final class FriendsWrapperAdapter extends XmlAdapter<FriendsWrapper, Shell<Set<Integer>>> {
        @Override
        public Shell<Set<Integer>> unmarshal(FriendsWrapper v) {
            return new Shell<>(v.getRooms());
        }
        @Override
        public FriendsWrapper marshal(Shell<Set<Integer>> v) {
            return new FriendsWrapper(v.safe());
        }
    }
}