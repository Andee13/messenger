package ua.sumdu.j2se.Birintsev.common.connection;

import ua.sumdu.j2se.Birintsev.client.UserData;
import ua.sumdu.j2se.Birintsev.common.connection.status.ConnectionResponseStatus;

import java.io.Serializable;

public class ConnectionResponse implements Serializable {
    private UserData userData;
    private ConnectionResponseStatus status;
    private String message;
    private Exception exception;

    public ConnectionResponse() {
        status = ConnectionResponseStatus.CREATED;
    }

    public ConnectionResponse(String message) {
        this.message = message;
        status = ConnectionResponseStatus.CREATED;
    }

    public ConnectionResponse(ConnectionResponseStatus status) {
        this.status = status;
    }

    public ConnectionResponse(Exception exception) {
        status = ConnectionResponseStatus.ERROR;
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    public UserData getUserData() {
        return userData;
    }

    public ConnectionResponseStatus getStatus() {
        return status;
    }

    public void setStatus(ConnectionResponseStatus status) {
        this.status = status;
    }

    public void setUserData(UserData userData) {
        this.userData = userData;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
