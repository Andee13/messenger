package server.exceptions;

import java.util.NoSuchElementException;

public class RoomNotFoundException extends NoSuchElementException {
    private final int roomId;
    public RoomNotFoundException(String message, int roomId) {
        super(message);
        this.roomId = roomId;
    }

    public RoomNotFoundException(int roomId) {
        this.roomId = roomId;
    }
}
