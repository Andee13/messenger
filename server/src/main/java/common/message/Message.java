package common.message;

import common.message.status.RequestStatus;
import common.message.status.ResponseStatus;
import common.message.status.Type;

import java.time.LocalDateTime;

public class Message {
    private LocalDateTime creationDateTime;
    private final Type type;
    private final Enum<? extends Enum> status;
    private String text;
    private String login;
    private String password;
    private Throwable exception;
    private int fromId = ID_DEFAULT_VALUE;
    private int toId = ID_DEFAULT_VALUE;
    private int roomId = ID_DEFAULT_VALUE;
    private static final int ID_DEFAULT_VALUE = -1;

    public Message(ResponseStatus status) {
        creationDateTime = LocalDateTime.now();
        type = Type.RESPONSE;
        this.status = status;
    }

    public Message(RequestStatus status) {
        creationDateTime = LocalDateTime.now();
        type = Type.REQUEST;
        this.status = status;
    }

    public int getRoomId() {
        return roomId;
    }

    public Message setRoomId(int roomId) {
        if (this.roomId != ID_DEFAULT_VALUE) {
            throw new IllegalStateException("\"roomId\" value is already set");
        }
        if (roomId < 0) {
            throw new IllegalArgumentException("Id must not be less than zero");
        }
        this.roomId = roomId;
        return this;
    }

    public Type getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public Message setText(String text) {
        if(this.text != null) {
            throw new IllegalStateException("The text is already set");
        }
        if (text == null) {
            throw new NullPointerException("Specified string must not be null");
        }
        this.text = text;
        return this;
    }

    public String getLogin() {
        return login;
    }

    public Message setLogin(String login) {
        if(this.login != null) {
            throw new IllegalStateException("The login is already set");
        }
        if (login == null) {
            throw new NullPointerException("Specified login String must not be null");
        }
        this.login = login;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public Message setPassword(String password) {
        if (password == null) {
            throw new NullPointerException("Specified string must not be null");
        }
        if(this.password != null) {
            throw new IllegalStateException("The password is already set");
        }
        this.password = password;
        return this;
    }

    public Enum<? extends Enum> getStatus() {
        return status;
    }

    public Throwable getException() {
        return exception;
    }

    public Message setException(Throwable exception) {
        if (exception == null) {
            throw new NullPointerException("Specified exception must not be null");
        }
        if(this.exception != null) {
            throw new IllegalStateException("The exception is already set value is already set");
        }
        this.exception = exception;
        this.text = exception.getLocalizedMessage();
        return this;
    }

    public int getFromId() {
        return fromId;
    }

    public Message setFromId(int fromId) {
        if (this.fromId != ID_DEFAULT_VALUE) {
            throw new IllegalStateException("\"fromId\" value is already set");
        }
        if(fromId < 0) {
            throw new IllegalArgumentException("Id must not be less than zero");
        }
        this.fromId = fromId;
        return this;
    }

    public int getToId() {
        return toId;
    }

    public Message setToId(int toId) {
        if(this.toId != ID_DEFAULT_VALUE) {
            throw new IllegalStateException("\"toId\" value is already set");
        }
        if(toId < 0) {
            throw new IllegalArgumentException("Id must not be less than zero");
        }
        this.toId = toId;
        return this;
    }

    public Message setFromId(String stringToParse) {
        if (stringToParse == null) {
            throw new NullPointerException("Specified source string must not be null");
        }
        if(fromId != ID_DEFAULT_VALUE) {
            throw new IllegalStateException("\"fromId\" value is already set");
        }
        if(Integer.parseInt(stringToParse) < 0) {
            throw new IllegalArgumentException("Id must not be less than zero");
        }
        fromId = Integer.parseInt(stringToParse);
        return this;
    }

    public Message setToId(String stringToParse) {
        if (stringToParse == null) {
            throw new NullPointerException("Specified source string must not be null");
        }
        if(toId != ID_DEFAULT_VALUE) {
            throw new IllegalStateException("\"toId\" value is already set");
        }
        if(Integer.parseInt(stringToParse) < 0) {
            throw new IllegalArgumentException("Id must not be less than zero");
        }
        toId = Integer.parseInt(stringToParse);
        return this;
    }

    public Message setRoomId(String stringToParse) {
        if (stringToParse == null) {
            throw new NullPointerException("Specified source string must not be null");
        }
        if(roomId != ID_DEFAULT_VALUE) {
            throw new IllegalStateException("Room id is already set");
        }
        if(Integer.parseInt(stringToParse) < 0) {
            throw new IllegalArgumentException("Id must not be less than zero");
        }
        roomId = Integer.parseInt(stringToParse);
        return this;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", status=" + status +
                ", text='" + text + '\'' +
                ", login='" + login + '\'' +
                ", password='" + password + '\'' +
                ", exception=" + exception +
                ", fromId=" + fromId +
                ", toId=" + toId +
                ", roomId=" + roomId +
                '}';
    }

    public LocalDateTime getCreationDateTime() {
        return LocalDateTime.from(creationDateTime);
    }

    public Message setCreationDateTime(LocalDateTime creationDateTime) {
        this.creationDateTime = creationDateTime;
        return this;
    }
}