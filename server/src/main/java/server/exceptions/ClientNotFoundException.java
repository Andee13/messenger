package server.exceptions;

import java.util.NoSuchElementException;

/**
 * An instance of this exception marks up that the specified client has not been found where searched
 * For example it can be a {@code Room}, clients folder either friend list
 * */
public class ClientNotFoundException extends NoSuchElementException {
    private final int clientId;

    public ClientNotFoundException(int clientId) {
        this.clientId = clientId;
    }

    public int getClientId() {
        return clientId;
    }
}
