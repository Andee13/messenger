package common;

import common.message.Message;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import org.apache.log4j.Logger;
import server.Saveable;
import server.Server;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
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
        return roomId == room.roomId;
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
        return false;
    }

}