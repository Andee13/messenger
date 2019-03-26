package server.handlers;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import server.client.Client;
import server.client.ClientListener;
import server.room.Room;
import server.room.RoomProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import java.io.File;
import java.io.IOException;
import java.util.InvalidPropertiesFormatException;

import static common.Utils.buildMessage;

public class RegistrationRequestHandler extends RequestHandler {
    public RegistrationRequestHandler(Message message) {
        super(message);
    }

    public RegistrationRequestHandler(ClientListener clientListener, Message message) {
        super(clientListener, message);
    }

    @Override
    public Message handle() {
        return registration(message);
    }
    private Message registration(Message message) {
        if (message == null) {
            return new Message(MessageStatus.ERROR).setText("Message came as null");
        }
        if (!MessageStatus.REGISTRATION.equals(message.getStatus())) {
            return new Message(MessageStatus.ERROR).setText(buildMessage("Message of the",
                    MessageStatus.REGISTRATION, "was expected but found", message.getStatus()));
        }
        File clientsDir = new File(clientListener.getServer().getConfig().getProperty("clientsDir"));
        String login = message.getLogin();
        String password = message.getPassword();
        if (login == null || password == null) {
            return new Message(MessageStatus.ERROR).setText((login == null ? "login" : "password")
                    .concat(" has not been set"));
        }
        File clientDir = new File(clientsDir, String.valueOf(login.hashCode()));
        File clientFile = new File(clientDir, clientDir.getName().concat(".xml"));
        if (clientDir.isDirectory()) {
            return new Message(MessageStatus.DENIED)
                    .setText(buildMessage("The login", login, "is already taken"));
        }
        try {
            if (!clientDir.mkdir() || !clientFile.createNewFile()) {
                throw new IOException();
            }
        } catch (IOException e) {
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        Client client = new Client();
        try {
            client.setLogin(login);
            client.setServer(clientListener.getServer());
            client.setPassword(password);
            client.setClientId(login.hashCode());
            client.getRooms().safe().add(0);
            if (!clientListener.getServer().getOnlineClients().safe().containsKey(0)) {
                RoomProcessing.loadRoom(clientListener.getServer(), 0);
            }
            Room commomChat = clientListener.getServer().getOnlineRooms().safe().get(0);
            commomChat.getMembers().safe().add(client.getClientId());
            commomChat.save();
        } catch (NullPointerException e) {
            return new Message(MessageStatus.ERROR)
                    .setText("Check whether you have specified all the necessary parameters");
        } catch (InvalidPropertiesFormatException e) {
            LOGGER.error("Wrong properties");
            throw new RuntimeException(e);
        }
        if (!clientListener.getServer().getOnlineRooms().safe().containsKey(0)) {
            try {
                RoomProcessing.loadRoom(clientListener.getServer(), 0);
            } catch (InvalidPropertiesFormatException e) {
                LOGGER.error(buildMessage("Unknown server configuration error occurred", e.getMessage()));
                throw new RuntimeException(e);
            }
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(client, clientFile);
            LOGGER.info(buildMessage("New client id", client.getClientId(), "has been registered"));
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
        return new Message(MessageStatus.ACCEPTED)
                .setText(buildMessage("The account", login, "has been successfully created"));
    }
}