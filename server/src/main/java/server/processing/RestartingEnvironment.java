package server.processing;

import org.apache.log4j.Logger;
import server.Server;

import java.io.IOException;
import java.util.Properties;

import static common.Utils.buildMessage;

public class RestartingEnvironment extends Thread {
    private static final Logger LOGGER = Logger.getLogger("Restarter");
    private Server server;
    public RestartingEnvironment(Server server) {
        this.server = server;
    }
    @Override
    public void run() {
        Properties properties = server.getConfig();
        server.interrupt();
        //LocalDateTime timeout = LocalDateTime.now().plusSeconds(10);
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