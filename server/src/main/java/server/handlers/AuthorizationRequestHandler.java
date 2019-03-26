package server.handlers;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Level;
import server.client.Client;
import server.client.ClientListener;
import server.exceptions.ClientNotFoundException;
import server.processing.ServerProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.time.LocalDateTime;

import static common.Utils.buildMessage;

public class AuthorizationRequestHandler extends RequestHandler {
    public AuthorizationRequestHandler(Message message) {
        super(message);
    }

    public AuthorizationRequestHandler(ClientListener clientListener, Message message) {
        super(clientListener, message);
    }

    @Override
    public Message handle() {
        Message responseMessage = auth(message);
        return responseMessage;
    }

    /**
     * The method that turns an incoming connection to a client's session
     * Verifies the {@code message} of status {@code MessageStatus.AUTH} comparing the incoming user data
     * such as a login and a password.
     *
     * @param message a message of {@code MessageStatus.AUTH} containing a login and a password
     *
     * @throws ClientNotFoundException  if the specified client's file has not been found
     *                                  in the {@code clientsDir} folder or there is not user data file
     * @throws NullPointerException     in case when message equals {@code null}
     */
    private Message auth(Message message) {
        if (message == null) {
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
        if (!MessageStatus.AUTH.equals(message.getStatus())) {
            String errorMessage = buildMessage("Message of the", MessageStatus.AUTH, "was expected but found"
                    , message.getStatus().toString());
            LOGGER.warn(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (message.getLogin() == null || message.getPassword() == null) {
            return new Message(MessageStatus.ERROR)
                    .setText((message.getLogin() == null ? "Login" : "Password").concat(" must be set"));
        }
        if (message.getFromId() != null) {
            return new Message(MessageStatus.ERROR).setText("Registration request must not have set fromId");
        }
        File clientFolder = new File(clientListener.getServer().getClientsDir()
                , String.valueOf(message.getLogin().hashCode()));
        File clientFile = new File(clientFolder, String.valueOf(message.getLogin().hashCode()).concat(".xml"));
        if (!clientFile.isFile()) {
            return new Message(MessageStatus.DENIED).setText("Please, check your password and login");
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            Client client = (Client) unmarshaller.unmarshal(clientFile);
            if (client.isBaned()) {
                if (LocalDateTime.now().isBefore(client.getIsBannedUntill())) {
                    return new Message(MessageStatus.DENIED).setText(buildMessage("You are banned until"
                            , ServerProcessing.DATE_TIME_FORMATTER.format(client.getIsBannedUntill())));
                } else {
                    client.setBaned(false);
                    client.setIsBannedUntil(null);
                    client.save();
                    LOGGER.trace(buildMessage("Client (id", client.getClientId(),
                            ") has been unbanned automatically (ban period is over)"));
                }
            }
            clientListener.setLogged(client.getPassword().equals(message.getPassword()));
            if (clientListener.isLogged()) {
                clientListener.setClient(client);
                clientListener.getClient().setServer(clientListener.getServer());
                LOGGER.trace(buildMessage("Client (id", client.getClientId(), ") has logged in"));
                return new Message(MessageStatus.ACCEPTED);
            } else {
                if (LOGGER.isEnabledFor(Level.TRACE)) {
                    LOGGER.trace(buildMessage("Wrong password from client (id"
                            , String.valueOf(client.getClientId())));
                }
                return new Message(MessageStatus.DENIED).setText("Please, check your password and login");
            }
        } catch (JAXBException e) {
            LOGGER.fatal(e.getLocalizedMessage());
            return new Message(MessageStatus.ERROR).setText("Internal error");
        }
    }
}