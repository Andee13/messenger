package server;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import server.client.ClientListener;
import server.processing.ServerProcessing;
import server.room.Room;
import org.apache.log4j.Logger;

import java.util.ConcurrentModificationException;
import java.util.Map;

import static common.Utils.buildMessage;

/**
 *  The {@code Observer} class handles with the users who are AFK too long and rooms any member of which is not online
 *
 * @see             Server
 * @see             ServerProcessing
 * @see             ClientListener
 * @see             Room
 * */
public class Observer extends Thread {
    private Server server;
    private static volatile Logger LOGGER = Logger.getLogger(Observer.class.getSimpleName());

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

    protected Observer (Server server) {
        if (server == null) {
            throw new NullPointerException("The server must not be null");
        }
        this.server = server;
    }

    @Override
    public void run() {
        ObservableMap<Integer, ClientListener> onlineClients = FXCollections.synchronizedObservableMap(FXCollections
                .observableMap(server.getOnlineClients().safe()));
        ObservableMap<Integer, Room> onlineRooms = FXCollections.synchronizedObservableMap(FXCollections
                .observableMap(server.getOnlineRooms().safe()));
        while (!server.isInterrupted()) {
            // This loop saves the room in case if there is not longer any online member on a sever
            try {
                synchronized (server.getOnlineRooms().safe()) {
                    LOGGER.trace("Removing rooms");
                    for (Map.Entry<Integer, Room> roomWrapper : onlineRooms.entrySet()) {
                        if (roomWrapper.getValue().getRoomId() == 0) {
                            continue;
                        }
                        boolean toBeSavedAndReamoved = true;
                        for (int clientId : roomWrapper.getValue().getMembers().safe()) {
                            if (server.getOnlineClients().safe().containsKey(clientId)) {
                                toBeSavedAndReamoved = false;
                            }
                            if (!toBeSavedAndReamoved) {
                                break;
                            }
                        }
                        if (toBeSavedAndReamoved) {
                            LOGGER.trace(buildMessage("Saving the room (id", roomWrapper.getValue().getRoomId(), ')'));
                            server.getOnlineRooms().safe().remove(roomWrapper.getKey());
                            if (roomWrapper.getValue().save() && !server.getOnlineRooms().safe()
                                    .containsKey(roomWrapper.getKey())) {
                                LOGGER.info(buildMessage("Room (id", roomWrapper.getKey()
                                        , "has been saved by observer"));
                            } else {
                                LOGGER.warn(buildMessage("Room (id", roomWrapper.getKey()
                                        , ") has not been saved by observer properly"));
                            }
                        }
                    }
                }
            } catch(ConcurrentModificationException e){
                continue;
            }
            synchronized (server.getOnlineClients().safe()) {
                LOGGER.trace("Removing clients");
                for (Map.Entry<Integer, ClientListener> clientListenerWrapper : onlineClients.entrySet()) {
                    ClientListener clientListener = clientListenerWrapper.getValue();
                    if (clientListener.getSocket().isClosed()) {
                        server.getOnlineClients().safe().remove(clientListenerWrapper.getKey());
                        clientListener.interrupt();
                        if (!server.getOnlineClients().safe().containsKey(clientListenerWrapper.getKey())) {
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