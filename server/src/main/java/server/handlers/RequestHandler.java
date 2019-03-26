package server.handlers;

import common.entities.message.Message;
import org.apache.log4j.Logger;
import server.client.Client;
import server.client.ClientListener;

/**
 *  This class is a generalization on the all request handlers and represents a sequence of operations
 * to perform the required action
 * */
public abstract class RequestHandler {
    public static Logger LOGGER = Logger.getLogger(RequestHandler.class.getSimpleName());
    protected Message message;
    ClientListener clientListener;

    public RequestHandler(Message message) {
        this.message = message;
    }

    public RequestHandler(ClientListener clientListener, Message message) {
        this.message = message;
        this.clientListener = clientListener;
    }

    public void setClientListener(ClientListener clientListener) {
        this.clientListener = clientListener;
    }

    /**
     *  The method that contains logic of handling the request from client. It does not throw any exception, but if any
     * happens, returns an instance of {@code Message} with corresponding status
     *
     * @return          an instance of {@code Message} with information/result of request handling
     * */
    public abstract Message handle();
}