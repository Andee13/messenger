package server.room;

import common.message.Message;
import common.message.MessageStatus;
import javafx.collections.*;
import org.apache.log4j.Logger;
import server.ClientListener;
import server.Saveable;
import server.Server;
import server.ServerProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
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

    private void initMessageHistoryListener() {
        if (messageHistory == null) {
            messageHistory = FXCollections.synchronizedObservableList(FXCollections.observableList(new ArrayList<>()));
        }
        ((ObservableList<Message>)messageHistory).addListener((ListChangeListener<Message>) c -> {
            c.next();
            for (Message message : c.getAddedSubList()) {
                for (int clientId : members) {
                    if (server.getOnlineClients().containsKey(clientId)) {
                        ClientListener clientListener = server.getOnlineClients().get(clientId);
                        try {
                            Message messageToInformClient = message;
                            if (c.wasRemoved()) {
                                messageToInformClient = new Message(MessageStatus.REMOVED_MESSAGE)
                                        .setToId(message.getToId()).setFromId(message.getFromId())
                                        .setCreationDateTime(message.getCreationDateTime())
                                        .setRoomId(message.getRoomId()).setText(message.getText());
                            }
                            clientListener.sendResponseMessage(messageToInformClient);
                        } catch (IOException e) {
                            LOGGER.error(e.getLocalizedMessage());
                        }
                    }
                }
            }
        });
    }

    private void initMembersListener() {
        if (members == null) {
            members = FXCollections.synchronizedObservableSet(FXCollections.observableSet(new TreeSet<>()));
        }
        ((ObservableSet<Integer>)members).addListener((SetChangeListener<Integer>) change -> {
            for (int clientId : members) {
                if (clientId != change.getElementAdded() && server.getOnlineClients().containsKey(clientId)) {
                    ClientListener clientListener = server.getOnlineClients().get(clientId);
                    Message roomClientsChangeNotification;
                    if (change.wasRemoved()) {
                        roomClientsChangeNotification = new Message(MessageStatus.MEMBER_LEFT_ROOM).setRoomId(roomId)
                                .setFromId(change.getElementRemoved());
                    } else {
                        roomClientsChangeNotification = new Message(MessageStatus.NEW_ROOM_MEMBER).setRoomId(roomId)
                                .setFromId(change.getElementAdded());
                    }
                    try {
                        clientListener.sendResponseMessage(roomClientsChangeNotification);
                    } catch (IOException e) {
                        LOGGER.error(e.getLocalizedMessage());
                    }
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
        public Set<Integer> unmarshal(MembersObservableSetWrapper v) throws Exception {
            return v.getMembers();
        }

        @Override
        public MembersObservableSetWrapper marshal(Set<Integer> v) throws Exception {
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
        private List<Message> messages;

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

    private static final class MessageHistoryObservableListAdapter extends XmlAdapter<MessageHistoryObservableListWrapper, List<Message>> {
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
        if (!ServerProcessing.arePropertiesValid(serverProperties)) {
            throw new RuntimeException("Properties are not valid for a room to be saved into its file");
        }
        File roomsDir = new File(serverProperties.getProperty("roomsDir"));
        if (!roomsDir.isDirectory() || !roomsDir.mkdir()) {
            return false;
        }
        File roomDir = new File(roomsDir, String.valueOf(roomId));
        if (!roomDir.isDirectory() || !roomDir.mkdir()) {
            return false;
        }
        File roomFile = new File(roomDir, roomDir.getName().concat(".xml"));
        try {
            if (!roomFile.isFile() || !roomFile.createNewFile()) {
                return false;
            }
        } catch (IOException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(this, roomFile);
            // TODO distinguish the logic of saving checking to another (new) method
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Room unmarshalledRoom = (Room) unmarshaller.unmarshal(roomFile);
            return this.equals(unmarshalledRoom) && System.currentTimeMillis() - roomFile.lastModified() < 15000;
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
    }

}