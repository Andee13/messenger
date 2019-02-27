package server;

import common.message.Message;
import javafx.collections.FXCollections;
import org.apache.log4j.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name="client")
public class Client implements Saveable {
    @XmlElement
    private int clientId;
    @XmlJavaTypeAdapter(RoomsWrapperAdapter.class)
    private Set<Integer> rooms;
    @XmlJavaTypeAdapter(FriendsWrapperAdapter.class)
    private Set<Integer> friends;
    @XmlElement
    private String login;
    @XmlElement
    private String password;
    @XmlElement
    private boolean isAdmin;
    @XmlElement
    private boolean baned;
    @XmlJavaTypeAdapter(value = Message.LocalDateTimeAdapter.class)
    private LocalDateTime isBannedUntill;
    @XmlTransient
    private Server server;

    public LocalDateTime getIsBannedUntill() {
        return isBannedUntill;
    }

    public Client setIsBannedUntill(LocalDateTime isBannedUntill) {
        this.isBannedUntill = isBannedUntill;
        return this;
    }

    public boolean isBaned() {
        return baned;
    }

    public Client setBaned(boolean baned) {
        this.baned = baned;
        return this;
    }

    private static final Logger LOGGER = Logger.getLogger("Client");

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Client() {
        friends = FXCollections.synchronizedObservableSet(FXCollections.observableSet(new HashSet<>()));
        rooms = FXCollections.synchronizedObservableSet(FXCollections.observableSet(new HashSet<>()));
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public int getClientId() {
        return clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    public Set<Integer> getRooms() {
        return rooms;
    }

    public void setRooms(Set<Integer> rooms) {
        this.rooms = rooms;
    }

    public Set<Integer> getFriends() {
        return friends;
    }

    public void setFriends(Set<Integer> friends) {
        this.friends = friends;
    }

    public String getLogin() {
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

    private static class SetAdapter extends XmlAdapter<Integer[], Set<Integer>> {
        @Override
        public Set<Integer> unmarshal(Integer[] v) {
            return FXCollections.synchronizedObservableSet(FXCollections.observableSet(v));
        }
        @Override
        public Integer[] marshal(Set<Integer> v)  {
            Integer [] result = new Integer[v.size()];
            int index = 0;
            for (int i : v) {
                result[index++] = i;
            }
            return result;
        }
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
                && rooms.equals(that.getRooms())
                && friends.equals(that.getFriends());

    }

    @Override
    public boolean save() {
        if (server == null) {
            LOGGER.warn("The client saving has been failed: a server has not been set");
            return false;
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
            LOGGER.warn(new StringBuilder("The client saving has been failed: could not create a directory ")
                    .append(clientsDir.getAbsolutePath()));
            return false;
        }
        File clientDir = new File(clientsDir, String.valueOf(login.hashCode()));
        if (!clientDir.isDirectory()) {
            if (!clientDir.mkdir()) {
                LOGGER.warn(new StringBuilder("The client saving has been failed: could not create a directory ")
                        .append(clientDir.getAbsolutePath()));
                return false;
            }
        }
        File clientFile = new File(clientDir, String.valueOf(login.hashCode()).concat(".xml"));
        if (!clientDir.isDirectory()) {
            try {
                if (!clientDir.createNewFile()) {
                    LOGGER.warn(new StringBuilder("The client saving has been failed: could not create a directory ")
                            .append(clientDir.getAbsolutePath()));
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
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Client clientToCheck = (Client) unmarshaller.unmarshal(clientFile);
            return equals(clientToCheck);
        } catch (JAXBException e) {
            e.printStackTrace();
            LOGGER.warn(e.getLocalizedMessage());
            return false;
        }
    }

    public static Client from(File clientFile) throws FileNotFoundException {
        try {
            return (Client) JAXBContext.newInstance(Client.class).createUnmarshaller().unmarshal(clientFile);
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

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

    private static final class RoomsWrapperAdapter extends XmlAdapter<RoomsWrapper, Set<Integer>> {
        @Override
        public Set<Integer> unmarshal(RoomsWrapper v) {
            return v.getRooms();
        }
        @Override
        public RoomsWrapper marshal(Set<Integer> v) {
            return new RoomsWrapper(v);
        }
    }

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

    private static final class FriendsWrapperAdapter extends XmlAdapter<FriendsWrapper, Set<Integer>> {
        @Override
        public Set<Integer> unmarshal(FriendsWrapper v) {
            return v.getRooms();
        }
        @Override
        public FriendsWrapper marshal(Set<Integer> v) {
            return new FriendsWrapper(v);
        }
    }
}