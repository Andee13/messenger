package server.processing;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Logger;
import server.client.Client;
import server.exceptions.ClientNotFoundException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.Properties;

import static common.Utils.buildMessage;

public class ClientProcessing {
    private static volatile Logger LOGGER = Logger.getLogger(ClientProcessing.class.getSimpleName());

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

    /**
     * This method is used to ban/unban a client having login like {@code} login. It just sends a message to server
     * and prints a response. It does not guarantees that client has been banned/unbanned
     *
     * @param           ban set is {@code true}
     *
     * */
    static void clientBan(Properties serverProperties, String login, boolean ban, int hours) {
        if (ban && hours < 1) {
            throw new IllegalArgumentException("hours: positive integer expected, but found "
                    .concat(String.valueOf(hours)));
        }
        try (Socket socket = new Socket("localhost", Integer.parseInt(serverProperties.getProperty("port")));
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(3000);
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            StringWriter stringWriter = new StringWriter();
            Message banMessage = new Message(ban ? MessageStatus.CLIENTBAN : MessageStatus.CLIENTUNBAN)
                    .setToId(login.hashCode())
                    .setLogin(serverProperties.getProperty("serverLogin"))
                    .setPassword(serverProperties.getProperty("serverPassword"));
            if (ban) {
                banMessage.setText(ServerProcessing.DATE_TIME_FORMATTER.format(LocalDateTime.now().plusHours(hours)));
            }
            marshaller.marshal(banMessage, stringWriter);
            dataOutputStream.writeUTF(stringWriter.toString());
            LOGGER.info(buildMessage("Server response:\n", dataInputStream.readUTF()));
        } catch (JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        } catch (SocketTimeoutException e) {
            LOGGER.error("Server does not response");
        } catch (IOException e) {
            LOGGER.error(buildMessage(e.getClass().getName(), e.getLocalizedMessage()));
        }
    }

    /**
     *  The method {@code hasAccountBeenRegistered} informs whether there is an account on the server
     * specified by the {@code serverProperties} with this {@code id}
     *
     * @return          {@code true} if and only if the properties being passed are valid and there is a registered
     *                  account having such login name on the server
     * */
    public static boolean hasNotAccountBeenRegistered(Properties serverProperties, int id) {
        if (!PropertiesProcessing.arePropertiesValid(serverProperties)) {
            LOGGER.error("Properties are not valid");
            return true;
        }
        File clientsDir = new File(serverProperties.getProperty("clientsDir"));
        File clientDir = new File(clientsDir, String.valueOf(id));
        File clientXml = new File(clientDir, String.valueOf(id).concat(".xml"));
        return !clientDir.isDirectory() || !clientXml.isFile();
    }

    /**
     *  The method {@code loadClient} unmarshalls a client parameters from the corresponding file of a server.
     *
     * @param           serverProperties a set of server configuration
     *
     * @param           clientId an id of the client to be unmarhalled
     * */
    public static Client loadClient(Properties serverProperties, int clientId) {
        if (!clientExists(serverProperties, clientId)) {
            throw new ClientNotFoundException(clientId);
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Client.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            File clientFile = getClientFile(serverProperties, clientId);
            return (Client) unmarshaller.unmarshal(clientFile);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The method {@code clientExists} informs whether there is such client registered on the server
     *
     *  NOTE! This method will not inform you in case if you enter invalid properties, {@code null} or
     * some kind of exception has occurred. It will just return {@code false} if it failed to
     * get the client's parameters.
     *
     * @param           serverProperties a set of server configurations
     * @param           clientId client's id to be searched
     *
     * @return          {@code true} if and only if the server denoted by {@code serverProperties} exists
     *                  a client with such {@code clientId} has been registered on the server
     * */
    private static boolean clientExists(Properties serverProperties, int clientId) {
        try {
            if (!PropertiesProcessing.arePropertiesValid(serverProperties)) {
                return false;
            }
            File clientFile = getClientFile(serverProperties, clientId);
            return clientFile.isFile();
        } catch (Throwable e) {
            LOGGER.error(e.getLocalizedMessage());
            return false;
        }
    }

    private static File getClientFile(Properties serverConfig, int clientId) {
        File clientsFolder = new File(serverConfig.getProperty("clientsDir"));
        File clientFolder = new File(clientsFolder, String.valueOf(clientId));
        return new File(clientFolder, clientFolder.getName().concat(".xml"));
    }
}