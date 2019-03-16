package server.processing;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import server.Observer;
import server.Server;
import server.client.Client;
import server.client.ClientListener;
import server.room.Room;
import server.room.RoomProcessing;

import java.io.File;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import static common.Utils.buildMessage;
import static server.processing.PropertiesProcessing.arePropertiesValid;

public class LoggersProcessing {
    public static void resetLoggers() {
        PropertyConfigurator.configure(LoggersProcessing.class.getResourceAsStream("../../log4j.properties"));
        Client.setLogger(Logger.getLogger(Client.class.getSimpleName()));
        ClientListener.setLogger(Logger.getLogger(ClientListener.class.getSimpleName()));
        Server.setLogger(Logger.getLogger(Server.class.getSimpleName()));
        Observer.setLogger(Logger.getLogger(Observer.class.getSimpleName()));
        Room.setLogger(Logger.getLogger(Room.class.getSimpleName()));
        RoomProcessing.setLogger(Logger.getLogger(RoomProcessing.class.getSimpleName()));
        ClientProcessing.setLogger(Logger.getLogger(ClientProcessing.class.getSimpleName()));
        PropertiesProcessing.setLogger(Logger.getLogger(PropertiesProcessing.class.getSimpleName()));
        RestartingEnvironment.setLogger(Logger.getLogger(RestartingEnvironment.class.getSimpleName()));
        ServerProcessing.setLogger(Logger.getLogger(ServerProcessing.class.getSimpleName()));
    }

    /**
     *  This method provides the system with information where the logger files have to be stored
     *
     * @param           serverConfig the server configurations
     *
     * @throws InvalidPropertiesFormatException in case if the passed properties are npt valid
     * */
    public static void setLoggersFilesSysProperties(Properties serverConfig) throws InvalidPropertiesFormatException {
        if (!arePropertiesValid(serverConfig)) {
            throw new InvalidPropertiesFormatException("The passed properties are not valid");
        }
        File logsDir = new File(serverConfig.getProperty("logsDir"));
        System.setProperty("observerLogFile", new File(logsDir, serverConfig.getProperty("observerLogFile"))
                .getAbsolutePath());
        System.setProperty("serverLogFile", new File(logsDir, serverConfig.getProperty("serverLogFile"))
                .getAbsolutePath());
        System.setProperty("clientListenerLogFile", new File(logsDir, serverConfig.getProperty("clientListenerLogFile"))
                .getAbsolutePath());
        System.setProperty("roomLogFile", new File(logsDir, serverConfig.getProperty("roomLogFile"))
                .getAbsolutePath());
        System.setProperty("roomProcessingLogFile", new File(logsDir, serverConfig.getProperty("roomProcessingLogFile"))
                .getAbsolutePath());
        System.setProperty("serverProcessingLogFile", new File(logsDir, serverConfig.getProperty("serverProcessingLogFile"))
                .getAbsolutePath());
        System.setProperty("clientLogFile", new File(logsDir, serverConfig.getProperty("clientLogFile")).getAbsolutePath());
        System.setProperty("propertiesProcessingLogFile", new File(logsDir, serverConfig.getProperty("propertiesProcessingLogFile"))
                .getAbsolutePath());
        System.setProperty("clientProcessingLogFile", new File(logsDir, serverConfig.getProperty("clientProcessingLogFile"))
                .getAbsolutePath());
        System.setProperty("restarterLogFile", new File(logsDir, serverConfig.getProperty("restarterLogFile"))
                .getAbsolutePath());
    }

    public static void setDefaultLoggersFiles(File currentDir) {
        if (!currentDir.isDirectory()) {
            throw new RuntimeException(buildMessage("The passed folder is not an existing directory "
                    , currentDir.getAbsolutePath()));
        }
        System.setProperty("observerLogFile", new File(currentDir, "observer.log")
                .getAbsolutePath());
        System.setProperty("serverLogFile", new File(currentDir, "server.log")
                .getAbsolutePath());
        System.setProperty("clientListenerLogFile", new File(currentDir, "clientListener.log")
                .getAbsolutePath());
        System.setProperty("roomLogFile", new File(currentDir, "room.log")
                .getAbsolutePath());
        System.setProperty("roomProcessingLogFile", new File(currentDir, "roomProcessing.log")
                .getAbsolutePath());
        System.setProperty("serverProcessingLogFile", new File(currentDir, "serverProcessing.log")
                .getAbsolutePath());
        System.setProperty("clientLogFile", new File(currentDir, "client.log").getAbsolutePath());
        System.setProperty("propertiesProcessingLogFile", new File(currentDir, "propertiesProcessing.log")
                .getAbsolutePath());
        System.setProperty("clientProcessingLogFile", new File(currentDir, "clientProcessing.log")
                .getAbsolutePath());
        System.setProperty("restarterLogFile", new File(currentDir, "restarter.log")
                .getAbsolutePath());
    }
}