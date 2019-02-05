package server;

import common.message.Message;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.io.IOException;
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

    public Room(){
        messageHistory = FXCollections.observableArrayList(new ArrayList<>());
        members = FXCollections.observableSet(new HashSet<>());
    }

    public  static Room getRoom(int roomId) throws JAXBException {
        File roomFile = new File(new StringBuilder(Server.getRoomsDir().getAbsolutePath())
                .append(roomId).append(".xml").toString());
        if(roomFile.exists()) {
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Room room = (Room) unmarshaller.unmarshal(roomFile);
            return room;
        }
        throw new NoSuchElementException(new StringBuilder("The room id ")
                .append(roomId).append(" has not been found").toString());
    }

    public Room createRoom(int adminId, int... userIds) throws IOException, JAXBException {
        /*Room room = new Room();
        room.setAdminId(adminId);
        for(int userId : userIds){
            room.getMembers().add(userId);
        }
        room.roomId = server.getAmOfRooms() + 1;
        server.setAmOfRooms(server.getAmOfRooms() + 1);
        File file = new File(new StringBuilder(
                server.getClientsDir().getAbsolutePath()).append(room.roomId).append(".xml").toString());
        if(!file.exists()){
            file.createNewFile();
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(room, file);
            return room;
        }
        throw new RuntimeException(new StringBuilder("The file ")
                .append(file.getAbsolutePath()).append(" already exists"). toString());*/
        return null;
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