package common.entities.message;

import server.processing.ServerProcessing;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.LocalDateTime;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "message")
public class Message {
    @XmlJavaTypeAdapter(value = Message.LocalDateTimeAdapter.class)
    private LocalDateTime creationDateTime;
    private MessageStatus status;
    private String text;
    private String login;
    private String password;
    private Integer fromId;
    private Integer toId;
    private Integer roomId;

    @SuppressWarnings("unused")
    public Message() {
        setCreationDateTime(LocalDateTime.now());
    }

    public Message(MessageStatus status) {
        setCreationDateTime(LocalDateTime.now());
        this.status = status;
    }

    public Message setStatus(MessageStatus status) {
        this.status = status;
        return this;
    }

    @SuppressWarnings("unused")
    public Message setFromId(Integer fromId) {
        this.fromId = fromId;
        return this;
    }

    public Message setToId(Integer toId) {
        this.toId = toId;
        return this;
    }

    public Message setRoomId(Integer roomId) {
        this.roomId = roomId;
        return this;
    }

    public Integer getRoomId() {
        return roomId;
    }

    public Message setRoomId(int roomId) {
        this.roomId = roomId;
        return this;
    }

    public String getText() {
        return text;
    }

    public Message setText(String text) {
        this.text = text;
        return this;
    }

    public String getLogin() {
        return login;
    }

    public Message setLogin(String login) {
        this.login = login;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public Message setPassword(String password) {
        this.password = password;
        return this;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Integer getFromId() {
        return fromId;
    }

    public Message setFromId(int fromId) {
        this.fromId = fromId;
        return this;
    }

    public Integer getToId() {
        return toId;
    }

    public Message setToId(int toId) {
        this.toId = toId;
        return this;
    }

    public Message setRoomId(String stringToParse) {
        roomId = Integer.parseInt(stringToParse);
        return this;
    }

    public String toString() {
        return "Message{" +
                "creationDateTime=" + creationDateTime +
                ", status=" + status +
                ", text='" + text + '\'' +
                ", login='" + login + '\'' +
                ", password='" + password + '\'' +
                ", fromId=" + fromId +
                ", toId=" + toId +
                ", roomId=" + roomId +
                '}';
    }

    private void setCreationDateTime(LocalDateTime creationDateTime) {
        this.creationDateTime = creationDateTime;
    }

    public static class LocalDateTimeAdapter extends XmlAdapter<String, LocalDateTime> {
        public LocalDateTime unmarshal(String v) {
            return LocalDateTime.from(ServerProcessing.DATE_TIME_FORMATTER.parse(v));
        }
        public String marshal(LocalDateTime v) {
            return ServerProcessing.DATE_TIME_FORMATTER.format(v);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Message message = (Message) o;
        return creationDateTime.equals(message.creationDateTime) &&
                status == message.status &&
                Objects.equals(text, message.text) &&
                Objects.equals(login, message.login) &&
                Objects.equals(password, message.password) &&
                Objects.equals(fromId, message.fromId) &&
                Objects.equals(toId, message.toId) &&
                Objects.equals(roomId, message.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(creationDateTime, status, text, login, password, fromId, toId, roomId);
    }
}