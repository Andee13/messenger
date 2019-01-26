package ua.sumdu.j2se.Birintsev.client;

import java.io.File;
import java.io.Serializable;

public class UserData implements Serializable {
    String login;
    String password;
    String info;

    public UserData(String login, String password) {
        this.login = login;
        this.password = password;
    }

    public UserData() {
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}