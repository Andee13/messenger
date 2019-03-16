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
        System.setProperty("serverProcessingLogFile"
                , new File(logsDir, serverConfig.getProperty("serverProcessingLogFile"))
                .getAbsolutePath());
        System.setProperty("clientLogFile"
                , new File(logsDir, serverConfig.getProperty("clientLogFile")).getAbsolutePath());
        System.setProperty("propertiesProcessingLogFile"
                , new File(logsDir, serverConfig.getProperty("propertiesProcessingLogFile"))
                .getAbsolutePath());
        System.setProperty("clientProcessingLogFile"
                , new File(logsDir, serverConfig.getProperty("clientProcessingLogFile"))
                .getAbsolutePath());
        System.setProperty("restarterLogFile", new File(logsDir, serverConfig.getProperty("restarterLogFile"))
                .getAbsolutePath());
        System.setProperty("requestHandlerLogFile", new File(logsDir, serverConfig.getProperty("requestHandlerLogFile"))
                .getAbsolutePath());
    }

    static void setDefaultLoggersFiles() {
        if (!ServerProcessing.currentFolder.isDirectory()) {
            throw new RuntimeException(buildMessage("The passed folder is not an existing directory "
                    , ServerProcessing.currentFolder.getAbsolutePath()));
        }
        System.setProperty("observerLogFile", new File(ServerProcessing.currentFolder, "observer.log")
                .getAbsolutePath());
        System.setProperty("serverLogFile", new File(ServerProcessing.currentFolder, "server.log")
                .getAbsolutePath());
        System.setProperty("clientListenerLogFile", new File(ServerProcessing.currentFolder, "clientListener.log")
                .getAbsolutePath());
        System.setProperty("roomLogFile", new File(ServerProcessing.currentFolder, "room.log")
                .getAbsolutePath());
        System.setProperty("roomProcessingLogFile", new File(ServerProcessing.currentFolder, "roomProcessing.log")
                .getAbsolutePath());
        System.setProperty("serverProcessingLogFile", new File(ServerProcessing.currentFolder
                , "serverProcessing.log")
                .getAbsolutePath());
        System.setProperty("clientLogFile", new File(ServerProcessing.currentFolder, "client.log")
                .getAbsolutePath());
        System.setProperty("propertiesProcessingLogFile", new File(ServerProcessing.currentFolder
                , "propertiesProcessing.log")
                .getAbsolutePath());
        System.setProperty("clientProcessingLogFile", new File(ServerProcessing.currentFolder
                , "clientProcessing.log")
                .getAbsolutePath());
        System.setProperty("restarterLogFile", new File(ServerProcessing.currentFolder, "restarter.log")
                .getAbsolutePath());
        System.setProperty("requestHandlerLogFile", new File(ServerProcessing.currentFolder, "requestHandler.log")
                .getAbsolutePath());
    }
}