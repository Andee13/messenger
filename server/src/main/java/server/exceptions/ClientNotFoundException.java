package server.exceptions;

import java.util.NoSuchElementException;

/**
 * An instance of this exception marks up that the specified client has not been found where searched
 * For example it can be a {@code Room}, clients folder either friend list
 * */
public class ClientNotFoundException extends NoSuchElementException {
    private String message;

    public ClientNotFoundException(String message) {
        this.message = message;
    }

    public ClientNotFoundException(String s, String message) {
        super(s);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
