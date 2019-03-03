package messenger2.message;



import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

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

    //private static final Logger LOGGER = Logger.getLogger("Message");

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
            return LocalDateTime.from(DateTimeFormatter.ISO_DATE_TIME.parse(v));
        }

        public String marshal(LocalDateTime v) throws Exception {
            return  DateTimeFormatter.ISO_DATE_TIME.format(v);
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

    public static Message from (String xml) {
        if (xml == null) {
            throw new NullPointerException("xml must not be null");
        }
        try {
            return (Message) JAXBContext.newInstance(Message.class).createUnmarshaller()
                    .unmarshal(new StringReader(xml));
        } catch (JAXBException e) {
            //LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }
}