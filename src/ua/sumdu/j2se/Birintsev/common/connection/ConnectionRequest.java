package ua.sumdu.j2se.Birintsev.common.connection;

import ua.sumdu.j2se.Birintsev.common.connection.status.ConnectionRequestStatus;
import ua.sumdu.j2se.Birintsev.common.sendable.Sendable;

public class ConnectionRequest implements Sendable {
    private String login;
    private String password;
    private ConnectionRequestStatus connectionRequestStatus;
    private String info;

    public ConnectionRequest(String login, String password) {
        this.login = login;
        this.password = password;
        connectionRequestStatus = ConnectionRequestStatus.CREATED;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public ConnectionRequestStatus getStatus() {
        return connectionRequestStatus;
    }

    public void setStatus(ConnectionRequestStatus status) {
        this.connectionRequestStatus = status;
    }
}
