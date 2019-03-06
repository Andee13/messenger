package server.room;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import server.Server;
import server.ServerProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.util.*;

import server.exceptions.ClientNotFoundException;
import server.exceptions.RoomNotFoundException;

import static common.Utils.buildMessage;

/**
 * The class {@code RoomProcessing} is just a container of methods related with instances of the {@code Room} class
 * */
public class RoomProcessing {

    private static final Logger LOGGER = Logger.getLogger("RoomProcessing");

    /**
     *  The method {@code loadRoom} returns an instance of {@code Room} - representation of a place for communication
     * of two or more clients
     *
     *  !NOTE This method puts the room into the server online rooms map, that is why it may remove previous
     * instance of this room from the map.
     *
     * @param           roomId is an id of the room to be searched
     * @param           server a server containing {@code room}
     *
     * @throws          InvalidPropertiesFormatException if {@code serverConfig} is not valid e.g. is {@code null}
     *                  or the specified in the {@code serverConfig} filepath does not points an existing file
     *
     * @exception       RoomNotFoundException in case if there is not such room on the server
     *
     * @return          an instance of {@code Room} that has {@code roomId} equal to the specified by parameter
     * */
    public static Room loadRoom(Server server, int roomId) throws InvalidPropertiesFormatException {
        if (server == null) {
            LOGGER.error("Passed null server value");
            throw new NullPointerException("Server must not be null");
        }
        if (!ServerProcessing.arePropertiesValid(server.getConfig())) {
            throw new InvalidPropertiesFormatException("Server configurations are not valid");
        }
        File roomsDir = new File(server.getConfig().getProperty("roomsDir"));
        File roomDir = new File(roomsDir, String.valueOf(roomId));
        File roomFile = new File(roomDir, roomDir.getName().concat(".xml"));
        if(roomFile.isFile()) {
            try {
                LOGGER.trace("Loading the room id ".concat(String.valueOf(roomId)));
                JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                Room room = (Room) unmarshaller.unmarshal(roomFile);
                room.setServer(server);
                server.getOnlineRooms().put(roomId, room);
                return room;
            } catch (JAXBException e) {
                LOGGER.error(e.getLocalizedMessage());
                throw new RuntimeException(e);
            }
        } else {
            throw new RoomNotFoundException(
                    buildMessage("There is not such room (id", roomId, ") on the server"));
        }
    }

    /**
     *  The method {@code createRoom} registers a new room and adds the specified clients to it.
     * After it finishes work the new subfolder and room info file will be created in {@code roomsDir} of the server
     *
     * @param           adminId is an id of the room creator
     * @param           clientsIds is an array of the clients' ids that must be added to the room initially
     *
     * @return          an instance of the {@code Room} that has been created
     *                  or {@code null} if not
     *
     * @exception       ClientNotFoundException if of one of the passed ids does not match any registered ones
     *
     * @throws          InvalidPropertiesFormatException if {@code serverProperties} or the data it stores is not valid
     * */
    public static Room createRoom(Server server, int adminId, int... clientsIds)
            throws InvalidPropertiesFormatException {
        if (!ServerProcessing.arePropertiesValid(server.getConfig())) {
            throw new InvalidPropertiesFormatException("The specified server configurations are not valid");
        }
        if (!ServerProcessing.hasAccountBeenRegistered(server.getConfig(), adminId)) {
            throw new ClientNotFoundException(adminId);
        }
        for (int id : clientsIds) {
            if (!ServerProcessing.hasAccountBeenRegistered(server.getConfig(), id)) {
                throw new ClientNotFoundException(id);
            }
        }
        File roomsDir = new File(server.getConfig().getProperty("roomsDir"));
        if (!roomsDir.isDirectory()) {
            throw new RuntimeException("Unable to find the folder with rooms info ".concat(roomsDir.getAbsolutePath()));
        }
        int newRoomId;
        Random random = new Random(System.currentTimeMillis());
        do {
            newRoomId = random.nextInt();
        } while (newRoomId <= 0 || new File(roomsDir, String.valueOf(newRoomId)).isDirectory());
        File roomDir = new File(roomsDir, String.valueOf(newRoomId));
        if (!roomDir.mkdir()) {
            throw new RuntimeException("Unable to create room folder ".concat(roomDir.getAbsolutePath()));
        }
        Room newRoom = new Room();
        newRoom.setServer(server);
        newRoom.setAdminId(adminId);
        newRoom.setRoomId(newRoomId);
        newRoom.getMembers().add(adminId);
        synchronized (newRoom.getMembers()) {
            for (int clientId : clientsIds) {
                newRoom.getMembers().add(clientId);
            }
        }
        if (!newRoom.save()) {
            String errorMessage = "Unable to create new room id: ".concat(String.valueOf(newRoomId));
            LOGGER.error(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        try {
            return loadRoom(server, newRoomId);
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     *  The methods informs whether the file you are going to read is a representation of a {@code Room}
     *
     *  NOTE: if you pass invalid properties, the method will not throw any exception. It just will return {@code 0L}
     * in case if something went wrong whenever it had happened.
     *
     * It is supposed that method will be used for checking if the recent saved {@code Room} has been saved correctly.
     *
     *  Use this method must not be very frequently. Because it takes much resources
     * such as time and common system resources
     *
     * @param           serverProperties a set of a server configurations
     * @param           roomId an id of the room to be checked
     *
     * @return          an amount of milliseconds that have been lasted since the begin of the Unix epoch
     *                  or 0L if some kind of exception has occurred.
     * */
    public static long hasRoomBeenCreated(Properties serverProperties, int roomId) {
        try{
            if (!ServerProcessing.arePropertiesValid(serverProperties)) {
                LOGGER.warn("The passed properties are not valid");
                throw new InvalidPropertiesFormatException("Properties are not valid");
            }
            File roomsDir = new File(serverProperties.getProperty("roomsDir"));
            if (!roomsDir.isDirectory()) {
                return 0L;
            }
            File roomDir = new File(roomsDir, String.valueOf(roomId));
            if (!roomDir.isDirectory()) {
                return 0L;
            }
            File roomFile = new File(roomDir, String.valueOf(roomId).concat(".xml"));
            if (!roomFile.isFile()) {
                return 0L;
            }
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            unmarshaller.unmarshal(roomFile); // just for checking that it is possible to create a room from the file
            return roomFile.lastModified();
        } catch (Throwable e) {
            return 0L;
        }
    }

    /**
     *  The method {@code sendMessage} sends an instance of {@code Message} having status {@code MessageStatus.MESSAGE}
     * to the specified room of the server
     *
     * @param           server the server where where the room is located
     * @param           message the text message to be sent
     *
     * @return          {@code true} in case if message has been added to the room history
     *
     * @throws          IOException in case if some kind of I/O exception has occurred
     *                  e.g. {@code sever.getConfig()} does not return a valid configuration set,
     *
     * @exception       ClientNotFoundException in case if the client specified by the {@code message.getFromId()}
     *                  has not been registered on server or his/her data is unreachable
     * @exception       RoomNotFoundException in case if the room specified by the {@code message.getRoomId()}
     *                  has not been created on server or it's data is unreachable
     * */
    public static boolean sendMessage(@NotNull Server server, @NotNull Message message) throws IOException {
        // checking the message status
        if (message.getStatus() != MessageStatus.MESSAGE) {
            throw new IllegalArgumentException(buildMessage("Message status is expected to be"
                    , MessageStatus.MESSAGE, "but found",message.getStatus()));
        }
        // checking the properties
        if (!ServerProcessing.arePropertiesValid(server.getConfig())) {
            throw new InvalidPropertiesFormatException("The specified server has invalid configurations");
        }
        if (message.getFromId() == null) {
            throw new IOException("Unset addresser");
        }
        if (message.getRoomId() == null) {
            throw new IOException("Unset roomId");
        }
        if (message.getText() == null) {
            throw new IOException("Text has not been set");
        }
        int fromId = message.getFromId();
        int roomId = message.getRoomId();
        // Checking whether the specified user exists
        if (!ServerProcessing.hasAccountBeenRegistered(server.getConfig(), fromId)) {
            throw new ClientNotFoundException(fromId);
        }
        // Checking whether the specified room exists
        if (RoomProcessing.hasRoomBeenCreated(server.getConfig(), roomId) == 0) {
            throw new RoomNotFoundException("Unable to find room id: ".concat(String.valueOf(roomId)));
        }
        // Checking whether the specified room is in the server "online" rooms set
        if (!server.getOnlineRooms().containsKey(roomId)) {
            Map<Integer, Room> onlineRoms = server.getOnlineRooms();
            synchronized (onlineRoms) {
                onlineRoms.put(roomId, RoomProcessing.loadRoom(server, message.getRoomId()));
            }
        }
        Room room = server.getOnlineRooms().get(roomId);
        List<Message> messagesHistory = room.getMessageHistory();
        if (messagesHistory.size() >= Integer.parseInt(server.getConfig().getProperty("messageStorageDimension"))) {
            messagesHistory.set(messagesHistory.size() - 1, message);
            return true;
        }
        return server.getOnlineRooms().get(roomId).getMessageHistory().add(message);
    }
}