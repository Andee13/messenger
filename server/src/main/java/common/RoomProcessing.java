package common;

import org.apache.log4j.Logger;
import server.Server;
import server.ServerProcessing;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.util.*;

import server.exceptions.NoSuchClientException;

public class RoomProcessing {

    private static final Logger LOGGER = Logger.getLogger("Room");

    /**
     *  The method {@code getRoom} returns an instance of {@code Room} - representation of a place for communication
     * of two or more clients
     *
     * @param           roomId is an id of the room to be searched
     * @param           serverConfig a server configuration file
     *
     * @return          an instance of Room that has {@code roomId} equal to the specified parameter
     *                  if there is not such room in the rooms directory of the server
     *                  than the method will return {@code null}
     *
     * @throws          IOException if {@code serverConfig} is not valid e.g. is {@code null}
     *                  or the specified in the {@code serverConfig} filepath does not points an existing file
     * */
    public static Room getRoom(Properties serverConfig, int roomId) throws IOException {
        if (!ServerProcessing.arePropertiesValid(serverConfig)) {
            throw new IOException("Properties are not valid");
        }
        File roomFile = new File(new StringBuilder(serverConfig.getProperty("roomsFile"))
                .append(File.pathSeparator).append(roomId).append(".xml").toString());
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
     * @exception       NoSuchClientException if of one of the passed ids does not match any registered ones
     *
     * @throws          InvalidPropertiesFormatException if {@code serverConfig} or the data it stores is not valid
     * */

    public static Room createRoom(Properties serverConfig, int adminId, int... clientsIds)
            throws InvalidPropertiesFormatException {
        if (!ServerProcessing.arePropertiesValid(serverConfig)) {
            throw new InvalidPropertiesFormatException("The specified server configurations are not valid");
        }
        if (!ServerProcessing.hasAccountBeenRegistered(serverConfig, adminId)) {
            throw new NoSuchClientException(new StringBuilder("Unable to find client id ").append(adminId).toString());
        }
        for (int id : clientsIds) {
            if (!ServerProcessing.hasAccountBeenRegistered(serverConfig, id)) {
                throw new NoSuchClientException(new StringBuilder("Unable to find client id ").append(id).toString());
            }
        }
        File clientsDir = new File(serverConfig.getProperty("clientsDir"));
        Room newRoom = new Room();
        int newRoomId;
        Random random = new Random(System.currentTimeMillis());
        do {
            newRoomId = random.nextInt();
        } while (newRoomId <= 0 || new File(clientsDir, new StringBuilder(String.valueOf(newRoomId)).append(".xml").toString()).isFile());
        newRoom.setAdminId(adminId);
        newRoom.setRoomId(newRoomId);
        for (int clientId : clientsIds) {
            newRoom.getMembers().add(clientId);
        }
        saveRoom();
        try {
            return getRoom(serverConfig, newRoomId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void saveRoom(){
        // TODO saving the room to the "roomsDir" folder
    }
}