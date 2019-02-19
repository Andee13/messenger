package common.message;

import server.Server;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.time.LocalDateTime;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "message")
public class Message {
    @XmlJavaTypeAdapter(value = Message.LocalDateTimeAdapter.class)
    private LocalDateTime creationDateTime;
    @XmlElement
    private MessageStatus status;
    @XmlElement
    private String text;
    @XmlElement
    private String login;
    @XmlElement
    private String password;
    @XmlElement
    private Integer fromId;
    @XmlElement
    private Integer toId;
    @XmlElement
    private Integer roomId;

    public Message() {
        setCreationDateTime(LocalDateTime.now());
    }

    public Message(MessageStatus status) {
        setCreationDateTime(LocalDateTime.now());
        this.status = status;
    }

    public int getRoomId() {
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

    public int getFromId() {
        return fromId;
    }

    public Message setFromId(int fromId) {
        this.fromId = fromId;
        return this;
    }

    public int getToId() {
        return toId;
    }

    public Message setToId(int toId) {
        this.toId = toId;
        return this;
    }

    public Message setFromId(String stringToParse) {
        fromId = Integer.parseInt(stringToParse);
        return this;
    }

    public Message setToId(String stringToParse) {
        toId = Integer.parseInt(stringToParse);
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

    public LocalDateTime getCreationDateTime() {
        return creationDateTime;
    }

    public Message setCreationDateTime(LocalDateTime creationDateTime) {
        this.creationDateTime = creationDateTime;
        return this;
    }

    public static class LocalDateTimeAdapter extends XmlAdapter<String, LocalDateTime> {
        public LocalDateTime unmarshal(String v) throws Exception {
            return LocalDateTime.from(Server.dateTimeFormatter.parse(v));
        }

        public String marshal(LocalDateTime v) throws Exception {
            return Server.dateTimeFormatter.format(v);
        }
    }
}