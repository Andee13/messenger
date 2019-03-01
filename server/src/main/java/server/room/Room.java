package server.room;

import common.message.Message;
import common.message.MessageStatus;
import common.Saveable;
import javafx.collections.*;
import org.apache.log4j.Logger;
import server.*;
import server.client.ClientListener;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.IOException;
import java.util.*;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Room implements Saveable {
    @XmlElement
    private int roomId;
    @XmlElement
    private int adminId;
    @XmlJavaTypeAdapter(MessageHistoryObservableListAdapter.class)
    private List<Message> messageHistory;
    @XmlJavaTypeAdapter(MembersObservableSetAdapter.class)
    private Set<Integer> members;
    @XmlTransient
    private Server server;

    private static Logger LOGGER = Logger.getLogger("Room");

    public Room(){
        messageHistory = FXCollections.synchronizedObservableList(FXCollections.observableList(new ArrayList<>()));
        members = FXCollections.synchronizedObservableSet(FXCollections.observableSet(new TreeSet<>()));
        initMembersListener();
        initMessageHistoryListener();
    }

    /**
     *  This method initialize {@code messageHistory} list (synchronized + observable) and sets the observer, which will
     * inform every client whose id is currently stored in the server set of online clients.
     *
     *  NOTE! Current version of the server does not support the message removing operation
     *
     * @exception           UnsupportedOperationException in case if a message
     *                      has been removed from the {@code messageHistory}
     * */
    private void initMessageHistoryListener() {
        if (messageHistory == null) {
            messageHistory = FXCollections.synchronizedObservableList(FXCollections.observableList(new ArrayList<>()));
        }
        ((ObservableList<Message>)messageHistory).addListener((ListChangeListener<Message>) c -> {
            c.next();
            if (c.wasAdded()) {
                List <Message> sentMessages = (List<Message>) c.getAddedSubList();
                for (int clientId : members) {
                    if (server.getOnlineClients().containsKey(clientId)) {
                        for (Message message : sentMessages) {
                            server.getOnlineClients().get(clientId).sendMessageToConnectedClient(message);
                        }
                    }
                }
            } else {
                throw new UnsupportedOperationException("Current version of server does not support the operation of message deletion");
            }
        });
    }

    /**
     *  This method initialize {@code members} set (synchronized + observable) and sets the observer, which will
     *
     * */
    private void initMembersListener() {
        if (members == null) {
            members = FXCollections.synchronizedObservableSet(FXCollections.observableSet(new TreeSet<>()));
        }
        ((ObservableSet<Integer>)members).addListener((SetChangeListener<Integer>) change -> {
            Message notificationMessage;
            int clientId;
            if (change.wasAdded()) {
                clientId = change.getElementAdded();
                notificationMessage = new Message(MessageStatus.CLIENT_ONLINE).setFromId(clientId);
            } else { // change.wasRemoved() == true
                clientId = change.getElementRemoved();
                notificationMessage = new Message(MessageStatus.CLIENT_OFFLINE).setFromId(clientId);
            }
            for (Map.Entry<Integer, ClientListener> clientWrapper : server.getOnlineClients().entrySet()) {
                if (clientWrapper.getValue().getClient().getClientId() != clientId) {
                    clientWrapper.getValue().sendMessageToConnectedClient(notificationMessage);
                }
            }
        });
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public int getRoomId() {
        return roomId;
    }

    public int getAdminId() {
        return adminId;
    }

    public void setAdminId(int adminId) {
        this.adminId = adminId;
    }

    public List<Message> getMessageHistory() {
        return messageHistory;
    }

    private void setMessageHistory(ObservableList<Message> messageHistory) {
        this.messageHistory = messageHistory;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class MembersObservableSetWrapper {
        @XmlElement(name="clientId")
        private HashSet<Integer> members = new HashSet<>();

        public MembersObservableSetWrapper() {
        }

        public HashSet<Integer> getMembers() {
            return members;
        }

        public void setMembers(Set<Integer> members) {
            this.members = new HashSet<>(members);
        }
    }

    private static final class MembersObservableSetAdapter extends XmlAdapter<MembersObservableSetWrapper, Set<Integer>>{
        @Override
        public Set<Integer> unmarshal(MembersObservableSetWrapper v) {
            return v.getMembers();
        }
        @Override
        public MembersObservableSetWrapper marshal(Set<Integer> v) {
            MembersObservableSetWrapper membersObservableSetWrapper = new MembersObservableSetWrapper();
            membersObservableSetWrapper.setMembers(new HashSet<>(v));
            return membersObservableSetWrapper;
        }
    }

    @Override
    public String toString() {
        return "Room{" +
                "roomId=" + roomId +
                ", adminId=" + adminId +
                ", messageHistory=" + messageHistory +
                '}';
    }

    public Set<Integer> getMembers() {
        return members;
    }

    public void setMembers(ObservableSet<Integer> members) {
        this.members = members;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Room room = (Room) o;
        if (!room.getMembers().containsAll(members)
                || !members.containsAll(room.getMembers())
                || (members.toArray().length != room.getMembers().toArray().length)) {
            return false;
        }
        if (!room.getMessageHistory().containsAll(messageHistory)
                || !messageHistory.containsAll(room.getMessageHistory())
                || (messageHistory.toArray().length != room.getMessageHistory().toArray().length)) {
            return false;
        }
        if (room.roomId != roomId) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return roomId;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class MessageHistoryObservableListWrapper {
        @XmlElement(name="message")
        private List<Message> messages =
                FXCollections.synchronizedObservableList(FXCollections.observableList(new ArrayList<>()));
        public MessageHistoryObservableListWrapper() {
        }
        public MessageHistoryObservableListWrapper(List<Message> list) {
            FXCollections.synchronizedObservableList(FXCollections.observableList(list));
        }
        public List<Message> getMessages() {
            return messages;
        }
        public void setMessages(List<Message> messages) {
            this.messages = FXCollections.synchronizedObservableList(FXCollections.observableList(messages));
        }
    }

    private static final class MessageHistoryObservableListAdapter
            extends XmlAdapter<MessageHistoryObservableListWrapper, List<Message>> {
        public MessageHistoryObservableListAdapter() {
        }

        public List<Message> unmarshal(MessageHistoryObservableListWrapper messages) throws Exception {
            return FXCollections.synchronizedObservableList(FXCollections.observableList(messages.getMessages()));
        }

        public MessageHistoryObservableListWrapper marshal(List<Message> messages) throws Exception {
            return new MessageHistoryObservableListWrapper(messages);
        }
    }

    @Override
    public boolean save() {
        Properties serverProperties = server.getConfig();
        File roomsDir = new File(serverProperties.getProperty("roomsDir"));
        if (!roomsDir.isDirectory() && !roomsDir.mkdir()) {
            return false;
        }
        File roomDir = new File(roomsDir, String.valueOf(roomId));
        if (!roomDir.isDirectory() && !roomDir.mkdir()) {
            return false;
        }
        File roomFile = new File(roomDir, roomDir.getName().concat(".xml"));
        try {
            if (!roomFile.isFile() && !roomFile.createNewFile()) {
                return false;
            }
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(this, roomFile);
            return true;
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
    }
}