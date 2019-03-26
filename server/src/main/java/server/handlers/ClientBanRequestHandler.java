package server.handlers;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Level;
import org.jetbrains.annotations.NotNull;
import server.client.Client;
import server.client.ClientListener;
import server.exceptions.ClientNotFoundException;
import server.processing.ServerProcessing;

import java.time.DateTimeException;
import java.time.LocalDateTime;

import static common.Utils.buildMessage;
import static server.processing.ClientProcessing.loadClient;

public class ClientBanRequestHandler extends RequestHandler {
    public ClientBanRequestHandler(ClientListener clientListener, Message message) {
        super(clientListener, message);
    }

    @Override
    public Message handle() {
        Message responseMessage = clientBan(message);
        return responseMessage;
    }

    /**
     * The method {@code clientBan} handles with requests of blocking a user.
     *
     * @param           message an instance of {@code Message} that represents a request about blocking a user.
     *                          NOTE! It is expected that message contains following non-null fields
     *                          1) {@code fromId} - id of registered user who has admin rights
     *                              i.e. an instance of {@code Client} representing his account
     *                              has {@code isAdmin == true}
     *                          2)  {@code toId} - id of registered client who does not have admin
     *                              rights and is not banned
     *                          3)  {@code text} - a text representation of a {@code LocalDateTime} instance that points
     *                              the end of the ban (expected to be a future timestamp).
     *                              NOTE! It must be formatted using ServerProcessing.DATE_TIME_FORMATTER
     *
     * @return          an instance of {@code Message} that contains info about performed (or not) operation.
     *                  It may be of the following statuses
     *                      {@code MessageStatus.ACCEPTED}  -   if the specified client has been banned
     *                      {@code MessageStatus.DENIED}    -   if the specified client is an admin, already banned
     *                                                          or the client who sent this request
     *                                                          does not have admin rights
     *                      {@code MessageStatus.ERROR}     -   if an error occurred while executing the operation
     */
    private Message clientBan(@NotNull Message message) {
        String errorMessage;
        if (message.getToId() == null) {
            errorMessage = buildMessage("Attempt to ban unspecified account from "
                    , message.getFromId() != null ? message.getFromId() : " unspecified client");
            LOGGER.trace(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        if (clientListener.isMessageNotFromThisLoggedClient(message) && !(
                clientListener.getServer().getConfig().getProperty("serverLogin").equals(message.getLogin())
                        && clientListener.getServer().getConfig().getProperty("serverPassword")
                        .equals(message.getPassword())
        )) {
            errorMessage = "Wrong fromId or client has not log in";
            LOGGER.trace(errorMessage);
            return new Message(MessageStatus.DENIED).setText(errorMessage);
        }
        int toId = message.getToId();
        if (message.getText() == null) {
            errorMessage = "Attempt to ban client without specifying the term";
            LOGGER.trace(buildMessage(errorMessage, "from", message.getFromId(), "to", message.getToId()));
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        LocalDateTime bannedUntil;
        try {
            bannedUntil = LocalDateTime.parse(message.getText(), ServerProcessing.DATE_TIME_FORMATTER);
            if (LocalDateTime.now().isAfter(bannedUntil)) {
                throw new DateTimeException(
                        buildMessage("Passed the past date of the ban end:", message.getText()));
            }
        } catch (DateTimeException e) {
            errorMessage = buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage());
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(
                        buildMessage(errorMessage
                                , ". From id", message.getFromId(), "to id", message.getToId()));
            }
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        Client clientIsBeingBanned;
        try {
            if (clientListener.getServer().getOnlineClients().safe().containsKey(toId)) {
                clientIsBeingBanned = clientListener.getServer().getOnlineClients().safe().get(toId).getClient();
            } else {
                clientIsBeingBanned = loadClient(clientListener.getServer().getConfig(), toId);
            }
        } catch (ClientNotFoundException e) {
            errorMessage = buildMessage("Client (id", e.getClientId(), "has not been found");
            if (LOGGER.isEnabledFor(Level.ERROR)) {
                LOGGER.error(errorMessage);
            }
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
        clientIsBeingBanned.setServer(clientListener.getServer());
        boolean isAdmin = true;
        if (message.getFromId() != null) {
            isAdmin = clientListener.getServer().getOnlineClients().safe()
                    .get(message.getFromId()).getClient().isAdmin();
        }
        boolean isAlreadyBanned = clientIsBeingBanned.isBaned();
        boolean isBeingBannedAdmin = clientIsBeingBanned.isAdmin();
        if (!isAdmin || isBeingBannedAdmin || isAlreadyBanned || bannedUntil.isBefore(LocalDateTime.now())) {
            String deniedMessage = "Not enough rights to perform this operation: ".concat(
                    (!isAdmin || isBeingBannedAdmin) ? "not enough rights" :
                            (isAlreadyBanned) ? "the specified client is already banned" : "invalid date");
            LOGGER.trace(deniedMessage);
            return new Message(MessageStatus.DENIED).setText(deniedMessage);
        }
        if (clientListener.getServer().getOnlineClients().safe().containsKey(message.getToId())) {
            clientListener.getServer().getOnlineClients().safe().get(message.getToId()).interrupt();
        }
        clientIsBeingBanned.setBaned(true);
        clientIsBeingBanned.setIsBannedUntil(bannedUntil);
        if (clientIsBeingBanned.save()) {
            return new Message(MessageStatus.ACCEPTED)
                    .setText(buildMessage("The client id", toId, "has been banned"));
        } else {
            errorMessage = buildMessage("Unknown error. The client (id", clientIsBeingBanned.getClientId()
                    , ") has not been banned");
            LOGGER.warn(errorMessage);
            return new Message(MessageStatus.ERROR).setText(errorMessage);
        }
    }
}