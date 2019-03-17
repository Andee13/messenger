package server.processing;

import org.apache.log4j.Logger;
import server.Server;

import java.io.IOException;
import java.util.Properties;

import static common.Utils.buildMessage;

@SuppressWarnings("CanBeFinal")
public class RestartingEnvironment extends Thread {
    private static volatile Logger LOGGER = Logger.getLogger(RestartingEnvironment.class.getSimpleName());
    private Server server;

    public RestartingEnvironment(Server server) {
        this.server = server;
    }

    public static void setLogger(Logger logger) {
        LOGGER = logger;
    }

    @Override
    public void run() {
        Properties properties = server.getConfig();
        server.interrupt();
        while (!State.TERMINATED.equals(server.getState())) {
            LOGGER.trace("Waiting the server has shut down");
            try {
                sleep(2000);
            } catch (InterruptedException e) {
                LOGGER.error(buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
            }
        }
        try {
            LOGGER.trace("Starting the server");
            ServerProcessing.startServer(properties);
        } catch (IOException e) {
            LOGGER.error(buildMessage(e.getClass().getName(), "occurred:", e.getLocalizedMessage()));
        }
    }
}