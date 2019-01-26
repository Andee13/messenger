package ua.sumdu.j2se.Birintsev.client;

import ua.sumdu.j2se.Birintsev.common.Message;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionRequest;
import ua.sumdu.j2se.Birintsev.common.connection.ConnectionResponse;
import ua.sumdu.j2se.Birintsev.common.connection.status.ConnectionResponseStatus;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientMain {
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static Scanner scanner = new Scanner(System.in);
    private static Socket socket;
    private static Message message;

    public static void main(String[] args) {
        System.out.println("Login:");
        String login = scanner.nextLine();
        System.out.println("Password:");
        String password = scanner.nextLine();
        try {
            socket = new Socket("localhost",5940);
            System.out.println(socket);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            try {
                ConnectionResponse connectionResponse = connect(login, password);
                System.out.println(connectionResponse.getStatus());
                if(!connectionResponse.getStatus().equals(ConnectionResponseStatus.ACCEPTED)) {
                    socket.close();
                    return;
                }
            } catch(ClassNotFoundException e) {
                e.printStackTrace();
            }
            message = new Message("header message");
            while (!"close connection".equalsIgnoreCase(message.getText())){
                System.out.println("[SAY]: ");
                String string = scanner.nextLine();
                message = new Message(string);
                out.writeObject(message);
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ConnectionResponse connect (String login, String password) throws IOException, ClassNotFoundException{
        ConnectionRequest connectionRequest = new ConnectionRequest(login, password);
        out.writeObject(connectionRequest);
        ConnectionResponse connectionResponse = (ConnectionResponse)in.readObject();
        return connectionResponse;
    }

    /*private static ConnectionResponse registration(Socket socket, String login, String password){

    }*/
}