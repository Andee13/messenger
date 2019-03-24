package server.room;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.xml.sax.InputSource;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.NodeList;
import server.Server;
import server.processing.ClientProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;

import server.exceptions.ClientNotFoundException;
import server.exceptions.RoomNotFoundException;
import server.processing.PropertiesProcessing;

import static common.Utils.buildMessage;

/**
 * The class {@code PropertiesProcessing} is just a container for methods
 * related with instances of the {@code Room} class
 * */
public class RoomProcessing {
    private static volatile Logger LOGGER = Logger.getLogger("PropertiesProcessing");

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

    /**
     *  The method {@code loadRoom} returns an instance of {@code Room} - representation of a place for communication
     * of two or more clients
     *
     *  !NOTE This method puts the room into the server online rooms map, that is why it may remove previous
     * instance of this room from the map. It is recommended to check whether the room is in the map prior call.
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
        if (!PropertiesProcessing.arePropertiesValid(server.getConfig())) {
            throw new InvalidPropertiesFormatException("Server configurations are not valid");
        }
        File roomsDir = new File(server.getConfig().getProperty("roomsDir"));
        File roomDir = new File(roomsDir, String.valueOf(roomId));
        File roomFile = new File(roomDir, roomDir.getName().concat(".xml"));
        if(roomFile.isFile()) {
            try {
                LOGGER.trace(buildMessage("Loading the room id", String.valueOf(roomId)));
                JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                Room room = (Room) unmarshaller.unmarshal(roomFile);
                room.setServer(server);
                room.getMessageHistory().setMessageListener(message -> {
                synchronized (server.getOnlineClients().safe()) {
                    for (int clientId : room.getMembers().safe()) {
                        if (server.getOnlineClients().safe().containsKey(clientId)) {
                            server.getOnlineClients().safe().get(clientId)
                                    .sendMessageToConnectedClient(message.setStatus(MessageStatus.NEW_MESSAGE));
                        }
                    }
                }
                });
                server.getOnlineRooms().safe().put(roomId, room);
                return room;
            } catch (JAXBException e) {
                LOGGER.error(e.getLocalizedMessage());
                throw new RuntimeException(e);
            }
        } else {
            throw new RoomNotFoundException(
                    buildMessage("There is not such room on the server"), roomId);
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
        if (!PropertiesProcessing.arePropertiesValid(server.getConfig())) {
            throw new InvalidPropertiesFormatException("The specified server configurations are not valid");
        }
        if (ClientProcessing.hasNotAccountBeenRegistered(server.getConfig(), adminId)) {
            throw new ClientNotFoundException(adminId);
        }
        for (int id : clientsIds) {
            if (ClientProcessing.hasNotAccountBeenRegistered(server.getConfig(), id)) {
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
        newRoom.getMembers().safe().add(adminId);
        synchronized (newRoom.getMembers()) {
            for (int clientId : clientsIds) {
                newRoom.getMembers().safe().add(clientId);
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
            if (!PropertiesProcessing.arePropertiesValid(serverProperties)) {
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
     * @throws          IOException in case if some kind of I/O exception has occurred
     *                  e.g. {@code sever.getConfig()} does not return a valid configuration set,
     *
     * @exception       ClientNotFoundException in case if the client specified by the {@code message.getFromId()}
     *                  has not been registered on server or his/her data is unreachable
     * @exception       RoomNotFoundException in case if the room specified by the {@code message.getRoomId()}
     *                  has not been created on server or it's data is unreachable
     * */
    public static void sendMessage(@NotNull Server server, @NotNull Message message) throws IOException {
        // checking the message status
        if (message.getStatus() != MessageStatus.MESSAGE) {
            throw new IllegalArgumentException(buildMessage("Message status is expected to be"
                    , MessageStatus.MESSAGE, "but found",message.getStatus()));
        }
        // checking the properties
        if (!PropertiesProcessing.arePropertiesValid(server.getConfig())) {
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
        if (ClientProcessing.hasNotAccountBeenRegistered(server.getConfig(), fromId)) {
            throw new ClientNotFoundException(fromId);
        }
        // Checking whether the specified room exists
        if (RoomProcessing.hasRoomBeenCreated(server.getConfig(), roomId) == 0) {
            throw new RoomNotFoundException("Unable to find the room", roomId);
        }
        // Checking whether the specified room is in the server "online" rooms set
        if (!server.getOnlineRooms().safe().containsKey(roomId)) {
            synchronized (server.getOnlineRooms().safe()) {
                RoomProcessing.loadRoom(server, message.getRoomId());
            }
        }
        Room room = server.getOnlineRooms().safe().get(roomId);
        room.getMessageHistory().addMessage(message,true);
        room.save();
    }

    /**
     *  The method that informs if there is a member {@code clientId} in the room {@code roomId}
     * on server denoted by {@code serverProperties}
     *
     * @param           serverProperties a set of the server configurations
     * @param           clientId The client's clientId to be searched for
     * @param           roomId The room clientId where {@code clientId} will be searched
     *
     * @return          {@code true} if and only if the server denoted by this {@code serverProperties} exists
     *                  and there is a room id {@code roomId} with specified client id {@code clientId}
     * */
    public static boolean isMember(@NotNull Properties serverProperties, int clientId, int roomId) {
        if (!PropertiesProcessing.arePropertiesValid(serverProperties)
                || RoomProcessing.hasRoomBeenCreated(serverProperties, roomId) == 0L
                || !ClientProcessing.hasNotAccountBeenRegistered(serverProperties,clientId)) {
            return false;
        }
        XPath xPath = XPathFactory.newInstance().newXPath();
        XPathExpression xPathExpression;
        try {
            xPathExpression = xPath.compile("room/members/clientId");
        } catch (XPathExpressionException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
        File roomsDir = new File(serverProperties.getProperty("roomsDir"));
        File roomDir = new File(roomsDir, String.valueOf(roomId));
        File roomFile = new File(roomDir, String.valueOf(roomId).concat(".xml"));
        try {
            NodeList resultNodeList = (NodeList) xPathExpression.evaluate(
                    new InputSource(new BufferedReader(new FileReader(roomFile))), XPathConstants.NODESET);
            for (int i = 0; i < resultNodeList.getLength(); i++) {
                if(clientId == Integer.parseInt(resultNodeList.item(i).getTextContent())) {
                    return true;
                }
            }
        } catch (FileNotFoundException | XPathExpressionException e) {
            LOGGER.error(e.getLocalizedMessage());
            return false; // return false OR throw new RuntimeException(e); ?
        }
        return false;
    }
}