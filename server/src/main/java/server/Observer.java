package server;

import server.room.Room;
import common.message.Message;
import common.message.MessageStatus;
import org.apache.log4j.Logger;
import server.room.RoomProcessing;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

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
        Map<Integer, ClientListener> onlineClients = server.getOnlineClients();
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
            /*
            *   This loop saves the room in case if there is no longer online member of it on sever
            * */
            for (Map.Entry<Integer, Room> roomWrapper : server.getOnlineRooms().entrySet()) {
                boolean everyMemberIsoffline = true;
                for (int clientId : roomWrapper.getValue().getMembers()) {
                    if (server.getOnlineClients().containsKey(clientId)) {
                        everyMemberIsoffline = false;
                        break;
                    }
                }
                if (everyMemberIsoffline) {
                    roomWrapper.getValue().save();
                    server.getOnlineRooms().remove(roomWrapper.getKey());
                    if (!server.getOnlineRooms().containsKey(roomWrapper.getKey())
                            && System.currentTimeMillis() - RoomProcessing.hasRoomBeenCreated(server.getConfig(),
                                                                roomWrapper.getKey()) < 15000)
                    {
                        LOGGER.info(new StringBuilder("The room id ")
                                .append(roomWrapper.getKey()).append(" has been successfully saved"));
                    }
                }
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
