package server.exceptions;

import java.util.NoSuchElementException;

public class RoomNotFoundException extends NoSuchElementException {
    private final int roomId;
    public RoomNotFoundException(String message, int roomId) {
        super(message);
        this.roomId = roomId;
    }

    @SuppressWarnings("unused")
    public int getRoomId() {
        return roomId;
    }
}
