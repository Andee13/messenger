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
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;

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
     * The method {@code createRoom} registers a new room and adds the specified clients to it
     *
     * @param           adminId is an id of the room creator
     * @param           clientsIds is an array of the clients' ids that must be added to the room initially
     *
     * @return          an instance of the {@code Room} that has been created
     *                  or {@code null} if the room has not been created
     *
     * @exception       NoSuchElementException if of one of the passed ids does not match any registered client's id
     *
     * @throws          java.io.IOException if {@code serverConfig} or the data it stores is not valid
     * */

    public static Room createRoom(Properties serverConfig, int adminId, int... clientsIds) throws IOException {
        /*
         * Checking whether the clients with specified ids have been registered
         * */
        if(!ServerProcessing.arePropertiesValid(serverConfig)) {
            throw new IOException("Passed serverConfig is not valid");
        }

        File clientsDir = new File(new StringBuilder(serverConfig.getProperty("clientsDir")).toString());

        if (!new File(clientsDir.getAbsolutePath()
                .concat(File.pathSeparator).concat(String.valueOf(adminId)).concat(".xml")).isFile()) {
            throw new NoSuchElementException("Unable to find client id ".concat(String.valueOf(adminId)));
        }

        for (int clientId : clientsIds) {
            if (!new File(clientsDir.getAbsolutePath()
                    .concat(File.pathSeparator).concat(String.valueOf(adminId)).concat(".xml")).isFile()) {
                throw new NoSuchElementException("Unable to find client id ".concat(String.valueOf(clientId)));
            }
        }
        Room newRoom = new Room();
        newRoom.setAdminId(adminId);
        for(int id : clientsIds) {
            newRoom.getMembers().add(id); // adding members of the new room
        }
        File roomsDir = new File(serverConfig.getProperty("roomsDir"));
        File newRoomFile;
        /*
         * Trying to generate an id for the room
         * The loop prevents an id collision in case if it occurs
         * */
        do  {
            int newRoomId = Objects.hash(adminId, LocalDateTime.now());
            newRoom.setRoomId(newRoomId);
            newRoomFile = new File(
                    new StringBuilder(roomsDir.getAbsolutePath()).append(newRoomId).append(".xml").toString());
        } while (newRoomFile.exists());
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Room.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.marshal(newRoom, newRoomFile);
        } catch(JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
            throw new RuntimeException(e);
        }
        return getRoom(serverConfig, newRoom.getRoomId());
    }
}
