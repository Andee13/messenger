package messenger2.message;

public enum MessageStatus {
    AUTH,
    REGISTRATION,
    MESSAGE,
    USERBAN,
    CREATE_ROOM,
    DELETE_ROOM,
    INVITE_USER,
    UNINVITE_USER,
    ACCEPTED,
    DENIED,
    ERROR,
    STOP_SERVER,
    KICK,
    NEW_MESSAGE,
    NEW_ROOM_MEMBER,
    MEMBER_LEFT_ROOM,
    CLIENT_ONLINE,
    CLIENT_OFFLINE,
    FRIEND_LIST,
    ROOM_LIST
}
