package server;

import javafx.collections.FXCollections;
import org.apache.log4j.Logger;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;


@XmlRootElement(name="client")
public class Client implements Saveable{
    @XmlElement
    private int clientId;
    @XmlJavaTypeAdapter(SetAdapter.class)
    private Set<Integer> rooms;
    @XmlJavaTypeAdapter(SetAdapter.class)
    private Set<Integer> friends;
    @XmlElement
    private String login;
    @XmlElement
    private String password;
    @XmlElement
    private boolean isAdmin;
    private Server server;

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

    private static class SetAdapter extends XmlAdapter<HashSet<Integer>, Set<Integer>> {
        @Override
        public Set<Integer> unmarshal(HashSet<Integer> v) throws Exception {
            return FXCollections.synchronizedObservableSet(FXCollections.observableSet(v));
        }

        @Override
        public HashSet<Integer> marshal(Set<Integer> v) throws Exception {
            return new HashSet<>(v);
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

    /**
     * // TODO description
     * */
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
            if (!clientsDir.mkdir()) {
                LOGGER.warn(new StringBuilder("The client saving has been failed: could not create a directory ")
                        .append(clientsDir.getAbsolutePath()));
                return false;
            }
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
        if (!clientDir.isFile()) {
            try {
                if (!clientDir.createNewFile()) {
                    LOGGER.warn(new StringBuilder("The client saving has been failed: could not create a directory ")
                            .append(clientDir.getAbsolutePath()));
                }
            } catch (Exception e) {
                LOGGER.warn(e.getLocalizedMessage());
                return false;
            }
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(this, clientFile);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Client clientToCheck = (Client) unmarshaller.unmarshal(clientFile);
            return equals(clientToCheck);
        } catch (JAXBException e) {
            LOGGER.warn(e.getLocalizedMessage());
            return false;
        }
    }
}