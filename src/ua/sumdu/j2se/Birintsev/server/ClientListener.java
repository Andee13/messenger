package ua.sumdu.j2se.Birintsev.server;

import org.jetbrains.annotations.NotNull;
import ua.sumdu.j2se.Birintsev.client.UserData;
import ua.sumdu.j2se.Birintsev.common.Message;
import ua.sumdu.j2se.Birintsev.common.User;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionRequest;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionResponse;
import ua.sumdu.j2se.Birintsev.common.connection.status.ConnectionResponseStatus;
import ua.sumdu.j2se.Birintsev.common.sendable.Sendable;
import ua.sumdu.j2se.Birintsev.common.utill.IO;
import ua.sumdu.j2se.Birintsev.server.Server;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class ClientListener extends Thread{
    private Socket socket;
    private Server server;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientListener(@NotNull Server server) {
        this.server = server;
        socket = server.getCurrentSocket();
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            try {
                server.closeClientSession(this);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted() && !socket.isClosed()){
                try {
                    Sendable received = (Sendable) in.readObject();
                    handle(received);
                } catch (SocketException | ClassNotFoundException e) {
                    e.printStackTrace();
                    server.closeClientSession(this);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                server.closeClientSession(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void showMessage(Message message){

    }

    public Socket getSocket() {
        return socket;
    }

    public ObjectOutputStream getOut() {
        return out;
    }

    public ObjectInputStream getIn() {
        return in;
    }

    // TODO loger each if-else
    private void handle(Sendable sendable) throws IOException {
        if(sendable instanceof Message) {
            System.out.println(((Message) sendable).getText());
        } else if(sendable instanceof ConnectionRequest) {
            UserData userData = null;
            ConnectionResponse connectionResponse = null;
            switch (((ConnectionRequest) sendable).getStatus()) {
                case REGISTRATION:
                    File newUserDataFile = new File(new StringBuilder(Server.usersFolder).append(File.separatorChar).append(((ConnectionRequest) sendable).getLogin()).append(".txt").toString());
                    if(!newUserDataFile.createNewFile()){
                        connectionResponse = new ConnectionResponse(ConnectionResponseStatus.DENIED);
                        connectionResponse.setMessage(new StringBuilder("The login ").append(((ConnectionRequest) sendable).getLogin()).append(" is already taken").toString());
                        out.writeObject(connectionResponse);
                        break;
                    }
                    userData = new UserData(((ConnectionRequest) sendable).getLogin(), ((ConnectionRequest) sendable).getPassword());
                    userData.setInfo(((ConnectionRequest) sendable).getInfo());
                    IO.write(userData, newUserDataFile);
                    break;
                case LOGIN:
                    File userDataFile = new File(new StringBuilder(Server.usersFolder).append(File.separatorChar).append(((ConnectionRequest) sendable).getLogin()).append(".txt").toString());
                    if(!userDataFile.exists()){
                        connectionResponse = new ConnectionResponse(ConnectionResponseStatus.REGISTRATION_REQUESTED);
                        out.writeObject(connectionResponse);
                        break;
                    }
                    IO.read(userData, userDataFile);
                    if(!((ConnectionRequest) sendable).getPassword().equals(userData.getLogin())){
                        connectionResponse = new ConnectionResponse(ConnectionResponseStatus.DENIED);
                        connectionResponse.setMessage("Wrong password");
                        out.writeObject(connectionResponse);
                        break;
                    }
                    connectionResponse = new ConnectionResponse(ConnectionResponseStatus.ACCEPTED);
                    connectionResponse.setUserData(userData);
                    out.writeObject(connectionResponse);
                    break;
                case CREATED:
                    connectionResponse = new ConnectionResponse(ConnectionResponseStatus.ERROR);
                    connectionResponse.setMessage("Request status has not been set");
                    out.writeObject(connectionResponse);
                    break;
            }
        }
    }
}