package server;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import server.client.ClientListener;
import server.room.Room;
import org.apache.log4j.Logger;

import java.util.Map;

import static common.Utils.buildMessage;

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
    protected Observer (Server server) {
        if (server == null) {
            throw new NullPointerException("The server must not be null");
        }
        this.server = server;
    }
    @Override
    public void run() {
        ObservableMap<Integer, ClientListener> onlineClients = FXCollections.synchronizedObservableMap(FXCollections
                .observableMap(server.getOnlineClients()));
        ObservableMap<Integer, Room> onlineRooms = FXCollections.synchronizedObservableMap(FXCollections
                .observableMap(server.getOnlineRooms()));
        while (true) {
            /*
            *   This loop saves the room in case if there is not longer any online member on a sever
            * */
            synchronized (server.getOnlineRooms()) {
                for (Map.Entry<Integer, Room> roomWrapper : onlineRooms.entrySet()) {
                    boolean toBeSavedAndReamoved = true;
                    for (int clientId : roomWrapper.getValue().getMembers()) {
                        if (server.getOnlineClients().containsKey(clientId)) {
                            toBeSavedAndReamoved = false;
                        }
                        if (!toBeSavedAndReamoved) {
                            break;
                        }
                    }
                    if (toBeSavedAndReamoved) {
                        server.getOnlineRooms().remove(roomWrapper.getKey());
                        if (roomWrapper.getValue().save() && !server.getOnlineRooms().containsKey(roomWrapper.getKey())) {
                            LOGGER.info(buildMessage("Room (id", roomWrapper.getKey()
                                    , "has been saved by observer"));
                        } else {
                            LOGGER.warn(buildMessage("Room (id", roomWrapper.getKey()
                                    , ") has not been saved by observer properly"));
                        }
                    }
                }
            }
            synchronized (server.getOnlineClients()) {
                for (Map.Entry<Integer, ClientListener> clientListenerWrapper : onlineClients.entrySet()) {
                    ClientListener clientListener = clientListenerWrapper.getValue();
                    if (clientListener.getSocket().isClosed()) {
                        server.getOnlineClients().remove(clientListenerWrapper.getKey());
                        clientListener.interrupt();
                        if (!server.getOnlineClients().containsKey(clientListenerWrapper.getKey())) {
                            LOGGER.trace(buildMessage("Client (id", clientListenerWrapper.getKey()
                                    , ") has been removed from online clients by observer"));
                        } else {
                            LOGGER.warn(buildMessage("Attempt to remove client (id"
                                    , clientListenerWrapper.getKey(), ") has been failed by observer."
                                    , "ClientListener state is", clientListenerWrapper.getValue().getState()));
                        }
                    }
                }
            }

            try {
                sleep(60000);
            } catch (InterruptedException e) {
                LOGGER.fatal("Observer has been interrupted");
                break;
            }
        }
    }
}