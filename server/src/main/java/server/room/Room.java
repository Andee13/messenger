package server.room;

import common.entities.Shell;
import common.entities.message.Message;
import common.entities.message.MessageStatus;
import common.entities.Saveable;
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

import static common.Utils.buildMessage;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Room implements Saveable {
    private volatile int roomId;
    private volatile int adminId;
    @XmlJavaTypeAdapter(MessageHistoryObservableListAdapter.class)
    private volatile Shell<List<Message>> messageHistory;
    @XmlJavaTypeAdapter(MembersObservableSetAdapter.class)
    private volatile Shell<Set<Integer>> members;
    @XmlTransient
    private volatile Server server;

    private static Logger LOGGER = Logger.getLogger("Room");

    public Room() {
        ObservableList<Message> oHistory = FXCollections.synchronizedObservableList(
                FXCollections.observableList(new LinkedList<>()));
        ObservableSet<Integer> oMembers = FXCollections.synchronizedObservableSet(
                FXCollections.observableSet(new TreeSet<>()));
        initMembersListener(oMembers);
        initMessageHistoryListener(oHistory);
        messageHistory = new Shell<>(oHistory);
        members = new Shell<>(oMembers);
    }

    private void initMessageHistoryListener(ObservableList<Message> messageHistory) {
        messageHistory.addListener((ListChangeListener<Message>) c -> {
            c.next();
            if (c.wasAdded() && !c.wasRemoved()) {
                List <Message> sentMessages = (List<Message>) c.getAddedSubList();
                synchronized (members.safe()) {
                    for (int clientId : members.safe()) {
                        if (server.getOnlineClients().safe().containsKey(clientId)) {
                            for (Message message : sentMessages) {
                                // todo remove
                                LOGGER.info(buildMessage(c.getAddedSize(), "message(s) has been added"));
                                server.getOnlineClients().safe().get(clientId)
                                        .sendMessageToConnectedClient(message.setStatus(MessageStatus.NEW_MESSAGE));
                            }
                        }
                    }
                }
            } else if (c.wasRemoved() && !c.wasAdded()) {
                // TODO methods of removing the message from chat
            }
        });
    }

    public void initMembersListener(ObservableSet<Integer> members) {
        members.addListener((SetChangeListener<Integer>) change -> {
            Message notificationMessage;
            int clientId;
            if (change.wasAdded() && !change.wasRemoved()) {
                clientId = change.getElementAdded();
                notificationMessage = new Message(MessageStatus.NEW_ROOM_MEMBER).setFromId(clientId).setRoomId(roomId);
            } else if (change.wasRemoved() && !change.wasAdded()) { // change.wasRemoved() == true
                clientId = change.getElementRemoved();
                notificationMessage = new Message(MessageStatus.MEMBER_LEFT_ROOM).setFromId(clientId).setRoomId(roomId);
            } else {
                return;
            }

            synchronized (server.getOnlineClients().safe()) {
                for (Map.Entry<Integer, ClientListener> clientWrapper : server.getOnlineClients().safe().entrySet()) {
                    if (clientWrapper.getValue().getClient().getClientId() != clientId) {
                        clientWrapper.getValue().sendMessageToConnectedClient(notificationMessage);
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

    public Shell<List<Message>> getMessageHistory() {
        return messageHistory;
    }

    private static final class MembersObservableSetAdapter extends XmlAdapter<MembersObservableSetWrapper, Shell<Set<Integer>>>{
        @Override
        public Shell<Set<Integer>> unmarshal(MembersObservableSetWrapper v) {
            return new Shell<>(v.getMembers());
        }
        @Override
        public MembersObservableSetWrapper marshal(Shell<Set<Integer>> v) {
            MembersObservableSetWrapper membersObservableSetWrapper = new MembersObservableSetWrapper();
            membersObservableSetWrapper.setMembers(new HashSet<>(v.safe()));
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

    public Shell<Set<Integer>> getMembers() {
        return members;
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
        if (!room.getMembers().safe().containsAll(members.safe())
                || !members.safe().containsAll(room.getMembers().safe())
                || (members.safe().toArray().length != room.getMembers().safe().toArray().length)) {
            return false;
        }
        if (!room.getMessageHistory().safe().containsAll(messageHistory.safe())
                || !messageHistory.safe().containsAll(room.getMessageHistory().safe())
                || (messageHistory.safe().toArray().length != room.getMessageHistory().safe().toArray().length)) {
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

    @XmlAccessorType(XmlAccessType.FIELD)
    private static class MessageHistoryObservableListWrapper {
        @XmlElement(name="message")
        private LinkedList<Message> messages;
        public MessageHistoryObservableListWrapper(List<Message> list) {
            messages = new LinkedList<>(list);
        }
        public MessageHistoryObservableListWrapper() {
            messages = new LinkedList<>();
        }
        public LinkedList<Message> getMessages() {
            return messages;
        }
        public void setMessages(List<Message> messages) {
            this.messages = new LinkedList<>(messages);
        }
    }

    private static class MessageHistoryObservableListAdapter
            extends XmlAdapter<MessageHistoryObservableListWrapper, Shell<List<Message>>> {
        public Shell<List<Message>> unmarshal(MessageHistoryObservableListWrapper messages) {
            return new Shell<>(FXCollections.synchronizedObservableList(FXCollections.observableList(messages.getMessages())));
        }
        public MessageHistoryObservableListWrapper marshal(Shell<List<Message>> messages) {
            return new MessageHistoryObservableListWrapper(messages.safe());
        }
    }

    @Override
    public synchronized boolean save() {
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