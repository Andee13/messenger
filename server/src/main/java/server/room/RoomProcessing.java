package server.room;

import common.message.Message;
import common.message.MessageStatus;
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

/**
 * The class {@code RoomProcessing} is just a container of methods related with instances of the {@code Room} class
 * */
public class RoomProcessing {

    private static final Logger LOGGER = Logger.getLogger("Room");

    /**
     *  The method {@code getRoom} returns an instance of {@code Room} - representation of a place for communication
     * of two or more clients
     *
     * @param           roomId is an id of the room to be searched
     * @param           serverConfig a server configuration file
     *
     * @throws          IOException if {@code serverConfig} is not valid e.g. is {@code null}
     *                  or the specified in the {@code serverConfig} filepath does not points an existing file
     *
     * @return          an instance of Room that has {@code roomId} equal to the specified parameter
     *                  if there is not such room in the rooms directory of the server
     *                  than the method will return {@code null}
     * */
    public static Room getRoom(Properties serverConfig, int roomId) throws IOException {
        if (!ServerProcessing.arePropertiesValid(serverConfig)) {
            throw new IOException("Properties are not valid");
        }
        File roomFile = new File(new File(serverConfig.getProperty("roomsFile")), String.valueOf(roomId).concat(".xml"));
        if(roomFile.exists()) {
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                Room room = (Room) unmarshaller.unmarshal(roomFile);
                return room;
            } catch (JAXBException e) {
                LOGGER.error(e.getLocalizedMessage());
                throw new RuntimeException(e);
            }
        } else {
            return null;
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
     *                  or {@code null} if the room has not been created
     *
     * @exception ClientNotFoundException if of one of the passed ids does not match any registered ones
     *
     * @throws          InvalidPropertiesFormatException if {@code serverProperties} or the data it stores is not valid
     * */
    public static Room createRoom(Properties serverProperties, int adminId, int... clientsIds)
            throws InvalidPropertiesFormatException {
        if (!ServerProcessing.arePropertiesValid(serverProperties)) {
            throw new InvalidPropertiesFormatException("The specified server configurations are not valid");
        }
        if (!ServerProcessing.hasAccountBeenRegistered(serverProperties, adminId)) {
            throw new ClientNotFoundException("Unable to find client id ".concat(String.valueOf(adminId)));
        }
        for (int id : clientsIds) {
            if (!ServerProcessing.hasAccountBeenRegistered(serverProperties, id)) {
                throw new ClientNotFoundException("Unable to find client id ".concat(String.valueOf(id)));
            }
        }
        File clientsDir = new File(serverProperties.getProperty("clientsDir"));
        Room newRoom = new Room();
        int newRoomId;
        Random random = new Random(System.currentTimeMillis());
        do {
            newRoomId = random.nextInt();
        } while (newRoomId <= 0 || new File(clientsDir, String.valueOf(newRoomId).concat(".xml")).isFile());
        newRoom.setAdminId(adminId);
        newRoom.setRoomId(newRoomId);
        for (int clientId : clientsIds) {
            newRoom.getMembers().add(clientId);
        }
        saveRoom(serverProperties, newRoom);
        try {
            return getRoom(serverProperties, newRoomId);
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     *  The method {@code saveRoom} saves the specified representation of a chat-room into folder specified by
     * {@code serverProperties}. The {@code room} will be saved in XML representation.
     *  If it is invoked for a new room the room folder and file will be created.
     *
     * @param           serverProperties a set of server configuration
     * @param           room a room to be saved
     *
     * @throws          InvalidPropertiesFormatException if {@code serverProperties} are not valid
     *
     * @exception       NullPointerException if {@code room} is {@code null}
     * */
    public static void saveRoom(Properties serverProperties, Room room) throws InvalidPropertiesFormatException {
        if (!ServerProcessing.arePropertiesValid(serverProperties)) {
            throw new InvalidPropertiesFormatException("Server configurations are not valid: "
                    .concat(serverProperties.toString()));
        }
         if (room == null) {
             throw new NullPointerException("room must not be null");
         }
         File roomFolder = new File(new File(serverProperties.getProperty("roomsDir")),
                 String.valueOf(room.getRoomId()));
         if (!roomFolder.isDirectory()) {
             if (!roomFolder.mkdir()) {
                 LOGGER.error(new StringBuilder("Creating room folder ")
                         .append(roomFolder.getAbsolutePath()).append(" failed").toString());
                 throw new RuntimeException(new StringBuilder("Creating room folder ")
                         .append(roomFolder.getAbsolutePath()).append(" failed").toString());
             } else {
                 LOGGER.info(new StringBuilder("Creating room folder ")
                         .append(roomFolder.getAbsolutePath()).append(" succeed").toString());
             }
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
            Room room = (Room) unmarshaller.unmarshal(roomFile);
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
     * @throws          IOException in case if some kind of I/O exception has occured
     *                  e.g. {@code sever.getConfig()} does not return a valid configuration set,
     *
     * @exception       ClientNotFoundException in case if the client specified by the {@code message.getFromId()}
     *                  has not been registered on server or his/her data is unreachable
     * @exception       RoomNotFoundException in case if the room specified by the {@code message.getRoomId()}
     *      *                  has not been created on server or it's data is unreachable
     * */
    public static void sendMessage(@NotNull Server server, @NotNull Message message) throws IOException {
        if (message.getStatus() != MessageStatus.MESSAGE) {
            throw new IllegalArgumentException(new StringBuilder("Message status is expected to be ")
                    .append(MessageStatus.MESSAGE).append(" but found ").append(message.getStatus()).toString());
        }
        if (!ServerProcessing.arePropertiesValid(server.getConfig())) {
            throw new InvalidPropertiesFormatException("The specified server has invalid configurations");
        }
        int fromId = message.getFromId();
        int roomId = message.getRoomId();
        String text = message.getText();
        if (text == null) {
            throw new IOException("Text has not been set");
        }
        // Checking whether the specified user exists
        if (!ServerProcessing.hasAccountBeenRegistered(server.getConfig(), fromId)) {
            throw new ClientNotFoundException(new StringBuilder("There is not such client id: ")
                    .append(fromId).append(" registered on the server").toString());
        }
        // Checking whether the specified room exists
        long currentTime = System.currentTimeMillis();
        if (RoomProcessing.hasRoomBeenCreated(server.getConfig(), roomId) == 0) {
            throw new RoomNotFoundException("Unable to find room id: ".concat(String.valueOf(roomId)));
        }
        // Checking whether the specified room is in the server "online" rooms set
        if (!server.getOnlineRooms().containsKey(roomId)) {
            server.loadRoomToOnlineRooms(roomId);
        }
        Room room = server.getOnlineRooms().get(roomId);
        room.getMessageHistory().add(message);
    }
}