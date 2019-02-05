package server;

import common.message.Message;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import javax.xml.bind.*;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.*;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Room {
    @XmlElement
    private int roomId;
    @XmlElement
    private int adminId;
    @XmlJavaTypeAdapter(MessageHistoryObservableListAdapter.class)
    private ObservableList<Message> messageHistory;
    @XmlJavaTypeAdapter(MembersObservableSetAdapter.class)
    private ObservableSet<Integer> members;
    @XmlTransient
    private Server server;

    private Room(){
        messageHistory = FXCollections.observableArrayList(new ArrayList<>());
        members = FXCollections.observableSet(new HashSet<>());
    }

    /**
     * The method {@code getRoom} returns an instance of {@code Room} - representation of a place for communication
     * of two or more clients
     *
     * @param roomId is an id of the room to be searched
     *
     * @return an instance of Room that has {@code roomId} equal to the specified parameter
     *          if there is not such room in the rooms directory of the server
     *          than the method will return {@code null}
     *
     * */
    public static Room getRoom(int roomId) {
        File roomFile = new File(new StringBuilder(Server.getRoomsDir().getAbsolutePath())
                .append(roomId).append(".xml").toString());
        if(roomFile.exists()) {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                Room room = (Room) unmarshaller.unmarshal(roomFile);
                return room;
            } catch (JAXBException e) {
                // TODO logging the exceptions
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else {
            return null;
        }
    }

    /**
     * The method {@code createRoom} registers a new room and adds the specified clients to it
     *
     * @param adminId is an id of the room creator
     * @param clientsIds is an array of the clients' ids that must be added to the room initially
     *
     * @return an instance of the {@code Room} that has been created
     *          or {@code null} if the room has not been created
     *
     * @throws NoSuchElementException if of one of the passed ids does not match any registered client's id
     * */
    public static Room createRoom(int adminId, int... clientsIds) {
        /*
        * Checking whether the clients with specified ids have been registered
        * */
        if(!Server.clientExists(adminId)) { // checking the admin's id
            throw new NoSuchElementException(
                    new StringBuilder("There is not such client id (passed as an admin's id): ")
                            .append(adminId).append(" on the server").toString());
        }
        for(int i = 0; i < clientsIds.length; i++){ // checking other clients' ids
            if(!Server.clientExists(clientsIds[i])) {
                throw new NoSuchElementException(
                        new StringBuilder("There is not such client id: ")
                                .append(clientsIds[i]).append(" on the server").toString());
            }
        }
        Room newRoom = new Room();
        newRoom.adminId = adminId;
        for(int id : clientsIds) {
            newRoom.members.add(id); // adding members of the new room
        }
        File roomsDir = Server.getRoomsDir();
        File newRoomFile;
        /*
        * Trying to generate an id for the room
        * The loop prevents an id collision in case if it occurs
        * */
        do  {
            int newRoomId = Objects.hash(adminId, LocalDateTime.now());
            newRoom.roomId = newRoomId;
            newRoomFile = new File(
                    new StringBuilder(roomsDir.getAbsolutePath()).append(newRoomId).append(".xml").toString());
        } while (newRoomFile.exists());
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(newRoom, newRoomFile);
        } catch(JAXBException e) {
            // TODO logging the exception
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return getRoom(newRoom.roomId);
    }

    public int getRoomId() {
        return roomId;
    }

    public int getAdminId() {
        return adminId;
    }

    private void setAdminId(int adminId) {
        this.adminId = adminId;
    }

    public ObservableList<Message> getMessageHistory() {
        return messageHistory;
    }

    private void setMessageHistory(ObservableList<Message> messageHistory) {
        this.messageHistory = messageHistory;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class MessageHistoryObservableListWrapper {
        @XmlElement(name="message")
        private ArrayList<Message> messages = new ArrayList<>();

        public MessageHistoryObservableListWrapper() {
        }

        public ArrayList<Message> getMessages() {
            return messages;
        }

        public void setMessages(ArrayList<Message> messages) {
            this.messages = messages;
        }
    }

    private static final class MessageHistoryObservableListAdapter extends XmlAdapter<MessageHistoryObservableListWrapper, ObservableList<Message>> {
        public ObservableList<Message> unmarshal(MessageHistoryObservableListWrapper messages) throws Exception {
            return FXCollections.observableList(messages.getMessages());
        }

        public MessageHistoryObservableListWrapper marshal(ObservableList<Message> observableMessages) throws Exception {
            MessageHistoryObservableListWrapper messageHistoryObservableListWrapper = new MessageHistoryObservableListWrapper();
            messageHistoryObservableListWrapper.getMessages().addAll(observableMessages);
            return messageHistoryObservableListWrapper;
        }
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
        return roomId == room.roomId;
    }

    @Override
    public int hashCode() {
        return roomId;
    }
}