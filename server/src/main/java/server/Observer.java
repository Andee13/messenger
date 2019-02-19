package server;

import common.Room;
import common.message.Message;
import common.message.MessageStatus;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * The {@code Observer} class handles with the users who are AFK too long and rooms which do not have online users
 *
 * @see         Server
 * @see         ServerProcessing
 * @see         ClientListener
 * @see         Room
 * */
public class Observer extends Thread {
    private Server server;
    private static final Logger LOGGER = Logger.getLogger("Observer");
    protected Observer (Server server) throws IOException {
        if (server == null) {
            throw new NullPointerException("The server must not be null");
        }
        if (!ServerProcessing.arePropertiesValid(server.getConfig())) {
            throw new IOException("Server properties file is not valid");
        }
        this.server = server;
    }
    @Override
    public void run() {
        Map<Integer, ClientListener> onlineClients = server.getClients();
        Map<Integer, Room> onlineRooms = server.getOnlineRooms();

        while (true) {
            for (Map.Entry<Integer, ClientListener> entry : onlineClients.entrySet()) {
                ClientListener clientListener = entry.getValue();
                if (clientListener.getLastInputMessage().plusHours(1).isAfter(LocalDateTime.now())) {
                    Message kickMessage = new Message(MessageStatus.KICK).setText("You have been AFK too long");
                    try {
                        clientListener.sendResponseMessage(kickMessage);
                        server.closeClientSession(clientListener);
                    } catch (IOException e) {
                        LOGGER.error(e.getLocalizedMessage());
                    }
                }
            }
            for (Map.Entry<Integer, Room> entry : onlineRooms.entrySet()) {
                Room room = entry.getValue();
                Set<Integer> roomClients = room.getMembers();
                // TODO room saving + cleaning from the server set
            }

            try {
                sleep(60000);
            } catch (InterruptedException e) {
                LOGGER.fatal(e.getLocalizedMessage());
                break;
            }
        }
    }
}
