package server.client;

import common.entities.Shell;
import common.entities.message.Message;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import server.Server;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import static common.Utils.buildMessage;

/**
 *  The class {@code ClientListener} handles operating incoming connections i.e. it's methods
 * interact with requests from client side
 *
 *  NOTE! The methods are called in the method {@code handle(Message message)} do not throw any exceptions.
 * The point that their purpose is to execute an operation to be requested from client's side and return
 * a pieces of information about an executed (or not) operation. Thus in case if requested actions have not
 * been performed properly the methods return instances of {@code Message} of statuses {@code MessageStatus.ERROR}
 * or {@code MessageStatus.DENIED}. Some additional information may be provided in the field {@code Message.text}
 * */
@SuppressWarnings("CanBeFinal")
public class ClientListener extends Thread {

    private volatile Socket socket;
    private volatile Server server;
    private volatile Shell<DataOutputStream> out;
    private volatile Shell<DataInputStream> in;
    private boolean logged;
    private Client client;
    private RequestHandler requestHandler;

    Shell<DataOutputStream> getOut() {
        return out;
    }

    public Shell<DataInputStream> getIn() {
        return in;
    }

    public Client getClient() {
        return client;
    }

    private static volatile Logger LOGGER = Logger.getLogger(ClientListener.class.getSimpleName());

    public ClientListener(Server server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        requestHandler = new RequestHandler(this);
        out = new Shell<>(new DataOutputStream(new BufferedOutputStream(socket.getOutputStream())));
        in = new Shell<>(new DataInputStream(new BufferedInputStream(socket.getInputStream())));
    }

    public Server getServer() {
        return server;
    }

    public boolean isLogged() {
        return logged;
    }

    public void setLogged(boolean logged) {
        this.logged = logged;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public static void setLogger (Logger logger) {
        LOGGER = logger;
    }

    public Socket getSocket() {
        return socket;
    }

    @Override
    public void run() {
        if (server == null) {
            LOGGER.fatal("Server must not be null");
            interrupt();
            return;
        } else if (!State.RUNNABLE.equals(server.getState())) {
            LOGGER.fatal(buildMessage("Server must have", State.RUNNABLE
                    , "state, but currently it's state is", server.getState()));
            interrupt();
            return;
        }
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            String messageXml;
            socket.setSoTimeout(1000 /*ms*/ * 60 /*s*/ * 60 /*m*/);
            try {
                while (!isInterrupted()) {
                    messageXml = in.safe().readUTF();
                    requestHandler.handle((Message) unmarshaller.unmarshal(new StringReader(messageXml)));
                }
            } catch (SocketTimeoutException e) { // client disconnected
                String infoMessage = "The client";
                if (client != null) {
                    infoMessage = buildMessage(infoMessage, "(id", client.getClientId(), ')');
                }
                infoMessage = buildMessage(infoMessage, "disconnected (address"
                        , socket.getRemoteSocketAddress(), ')');
                LOGGER.info(infoMessage);
                if (client != null && !client.save()) {
                    LOGGER.warn(buildMessage("Saving the client (id", client.getClientId()
                            , ") has not been completed properly"));
                }
            } catch (IOException e) {
                String infoMessage = "Client";
                if (logged) {
                    infoMessage = buildMessage(infoMessage, "(id", client.getClientId(), ")");
                }
                infoMessage = buildMessage(infoMessage, "disconnected (address"
                        , socket.getRemoteSocketAddress(), ')');
                LOGGER.trace(infoMessage);
                if (client != null && !client.save()) {
                    LOGGER.warn(buildMessage("Saving the client (id", client.getClientId()
                            , ") has not been completed properly"));
                }
            }
        } catch (JAXBException e) { // unknown error
            LOGGER.fatal(e.getLocalizedMessage());
        } catch (SocketException e) {
            LOGGER.error(e.getLocalizedMessage());
        } finally {
            if (LOGGER.isEnabledFor(Level.TRACE)) {
                LOGGER.trace(buildMessage("Client (",
                                client == null ? "UNLOGGED" : buildMessage("id", client.getClientId())
                                , "disconnected"));
            }
            interrupt();
        }
    }

    public void sendMessageToConnectedClient(Message message) {
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Message.class);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            StringWriter stringWriter = new StringWriter();
            marshaller.marshal(message, stringWriter);
            out.safe().writeUTF(stringWriter.toString());
            out.safe().flush();
        } catch (IOException | JAXBException e) {
            LOGGER.error(e.getLocalizedMessage());
        }
    }

    @Override
    public void interrupt() {
        if (LOGGER.isEnabledFor(Level.TRACE)) {
            LOGGER.trace(buildMessage("Stopping the client ",
                    buildMessage((logged ? buildMessage("(id", client.getClientId(),')') : ("(not logged in)"))
                            , "disconnected"), " session"));
        }
        if (client != null && !client.save()) {
            LOGGER.error(buildMessage("Saving the client (id", client.getClientId()
                    , ") has not been finished properly"));
        }
        super.interrupt();
    }

    /**
     *  This methods may inform if the message is from current client
     *
     * @param           message a {@code Message} to be checked
     *
     * @return          {@code true} if and only if the client has logged in and his {@code clientId}
     *                  is equal to {@code fromId} of the {@code message}, {@code false otherwise}
     */
    public boolean isMessageNotFromThisLoggedClient(Message message) {
        if (message == null) {
            LOGGER.error("Passed null-message value to check the addresser id");
            return true;
        }
        if (!isLogged()) {
            LOGGER.trace("Passed message to check before log-in: ".concat(message.toString()));
            return true;
        }
        if (message.getFromId() == null || message.getFromId() != client.getClientId()) {
            LOGGER.info(buildMessage("Expected to receive clientId", client.getClientId()
                    , "but found", message.getFromId()));
            return true;
        }
        return false;
    }
}