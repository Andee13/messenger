package server.exceptions;

import java.util.NoSuchElementException;

/**
 * An instance of this exception marks up that the specified client has not been found where searched
 * For example it can be a {@code Room}, clients folder either friend list
 * */
public class ClientNotFoundException extends NoSuchElementException {
    private String message;
    private final int clientId;
    public ClientNotFoundException(int clientId, String message) {
        this.message = message;
        this.clientId = clientId;
    }

    public ClientNotFoundException(int clientId) {
        this.clientId = clientId;
    }

    public ClientNotFoundException(int clientId, String s, String message) {
        super(s);
        this.message = message;
        this.clientId = clientId;
    }

    public int getClientId() {
        return clientId;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
