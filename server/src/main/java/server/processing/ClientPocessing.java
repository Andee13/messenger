package server.processing;

import common.entities.message.Message;
import common.entities.message.MessageStatus;
import org.apache.log4j.Logger;
import server.Server;
import server.client.ClientListener;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

import static common.Utils.buildMessage;

public class ClientPocessing {
    private static final Logger LOGGER = Logger.getLogger(ClientPocessing.class.getSimpleName());
    /**
     * This method is used to ban/unban a client having login like {@code} login. It just sends a message to server
     * and prints a response. It does not guarantees that client has been banned/unbanned
     *
     * @param           ban set is {@code true}
     *
     * */
    public static void clientBan(Properties serverProperties, String login, boolean ban, int hours) {
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
                    .setLogin(serverProperties.getProperty("server_login"))
                    .setPassword(serverProperties.getProperty("server_password"));
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
     *  This method sends a {@code Message} of status {@code MessageStatus.AUTH} not specifying any additional parameters
     * Thus
     * */
    static Message sendAndWait(int port, int timeout) throws SocketTimeoutException {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1...65535, but found "
                    .concat(String.valueOf(port)));
        }
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be greater than 0, but found "
                    .concat(String.valueOf(timeout)));
        }
        try (Socket socket = new Socket("localhost", port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(timeout);
            try {
                JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
                Marshaller marshaller = jaxbContext.createMarshaller();
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                StringWriter stringWriter = new StringWriter();
                marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
                marshaller.marshal(new Message(MessageStatus.AUTH), stringWriter);
                out.writeUTF(stringWriter.toString());
                Message response = (Message) unmarshaller.unmarshal(new StringReader(in.readUTF()));
                if (MessageStatus.ERROR.equals(response.getStatus())
                        || MessageStatus.DENIED.equals(response.getStatus())) {
                    LOGGER.trace("Received expected answer ".concat(response.toString()));
                } else {
                    LOGGER.warn(buildMessage("Answer has been received but the status is",
                            response.getStatus(), ". Expected either", MessageStatus.ERROR, "or", MessageStatus.DENIED));
                }
                return response;
            } catch (JAXBException e) {
                LOGGER.error(buildMessage("Unknown JAXBException:", e.getLocalizedMessage()));
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *  The method {@code hasAccountBeenRegistered} informs whether there is an account on the server
     * specified by the {@code serverProperties} with this {@code id}
     *
     * @return          {@code true} if and only if the properties being passed are valid and there is a registered
     *                  account having such login name on the server
     * */
    public static boolean hasAccountBeenRegistered(Properties serverProperties, int id) {
        if (!PropertiesProcessing.arePropertiesValid(serverProperties)) {
            LOGGER.error("Properties are not valid");
            return false;
        }
        File clientsDir = new File(serverProperties.getProperty("clientsDir"));
        File clientDir = new File(clientsDir, String.valueOf(id));
        File clientXml = new File(clientDir, String.valueOf(id).concat(".xml"));
        return clientDir.isDirectory() && clientXml.isFile();
    }

    public static void saveClients(Server server) {
        if (server == null || server.getOnlineClients() == null) {
            String errorMessage = (server == null ? "A server" : "A set of online clients").concat(" has not been set");
            LOGGER.error(errorMessage);
            throw new NullPointerException(errorMessage);
        }
        synchronized (server.getOnlineClients().safe()) {
            for (Map.Entry<Integer, ClientListener> entry : server.getOnlineClients().safe().entrySet()) {
                if (entry.getValue().getClient() != null && !entry.getValue().getClient().save()) {
                    LOGGER.error(buildMessage("Client id", entry.getValue().getClient().getClientId(),
                            "has not been saved"));
                }
            }
        }
    }
}
