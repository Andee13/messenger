package ua.sumdu.j2se.Birintsev.server;

import javafx.collections.FXCollections;
import org.jetbrains.annotations.NotNull;
import ua.sumdu.j2se.Birintsev.client.UserData;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionRequest;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionResponse;
import ua.sumdu.j2se.Birintsev.common.connection.status.ConnectionResponseStatus;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;

public class Server extends Thread{
    private List<ClientListener> clients;
    private ServerSocket serverSocket;
    private Socket currentSocket;
    private ClientListener currentClient;
    public final static String usersFolder = new StringBuilder("res").append(File.separatorChar).append("users").toString();
    private ConnectionResponse connectionResponse;

    @Override
    public void run() {
        try {
            //serverSocket = new ServerSocket(5940,10, InetAddress.getByName("93.79.11.69")); // для соединения по интернету
            serverSocket = new ServerSocket(5940); // для соединения по локальной сети
            System.out.println(new StringBuilder("Server has been launched. ").append(serverSocket.getInetAddress()));
            clients = FXCollections.observableList(new LinkedList<>());
            while (true){
                currentSocket = serverSocket.accept();
                currentClient = new ClientListener(this);
                connectionResponse = login(currentClient);
                System.out.println(new StringBuilder("Connection status: ").append(connectionResponse.getStatus()).toString());
                currentClient.getOut().writeObject(connectionResponse);
                switch (connectionResponse.getStatus()){
                    case ACCEPTED:
                        clients.add(currentClient);
                        currentClient.start();
                        break;
                    case REGISTRATION_REQUESTED:
                        break;
                    case ERROR:
                        break;
                }
                currentClient = null;
                currentSocket = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Socket getCurrentSocket() {
        return currentSocket;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    // TODO logging exceptions
    public void close() {
        for (ClientListener client : clients){
            try {
                client.getSocket().close();
                clients.remove(client);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            serverSocket.close();
            interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeClientSession (@NotNull ClientListener client) throws IOException {
        client.getIn().close();
        client.getOut().close();
        client.getSocket().close();
        clients.remove(client);
        client.interrupt();
    }

    // TODO logging exceptions
    private ConnectionResponse login(@NotNull ClientListener client) throws IOException {
        try {
            ConnectionRequest connectionRequest = (ConnectionRequest) client.getIn().readObject();
            ConnectionResponse connectionResponse;
            File userDataFile = hasAccountBeenCreated(connectionRequest.getLogin());
            if (userDataFile == null) {
                connectionResponse = new ConnectionResponse(ConnectionResponseStatus.REGISTRATION_REQUESTED);
                connectionResponse.setMessage("User data file has not been found");
                return connectionResponse;
            }
            BufferedReader bufferedReader = new BufferedReader(new FileReader(userDataFile));
            if (!connectionRequest.getPassword().equals(bufferedReader.readLine())) {
                connectionResponse = new ConnectionResponse(ConnectionResponseStatus.DENIED);
                connectionResponse.setMessage("Incorrect password");
                return connectionResponse;
            }
            connectionResponse = new ConnectionResponse(ConnectionResponseStatus.ACCEPTED);
            UserData userData = new UserData();
            connectionResponse.setUserData(userData);
            return connectionResponse;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            ConnectionResponse error = new ConnectionResponse(e);
            return error;
        }
    }

    public File hasAccountBeenCreated(String login){
        File userDataFile = new File(
                new StringBuilder(usersFolder).append(File.separatorChar).append(login).append(".txt").toString()
        );
        if(userDataFile.exists()){
            return userDataFile;
        } else {
            return null;
        }
    }
}