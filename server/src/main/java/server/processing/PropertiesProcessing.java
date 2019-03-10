package server.processing;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static common.Utils.buildMessage;

public class PropertiesProcessing {
    /**
     *  The method {@code arePropertiesValid} checks if the passed abstract path is a valid file.
     * Returns {@code true} if and only if the specified by the abstract path file exists and contains
     * properties about existing clients and rooms directories, {@code false} otherwise.
     *
     * @param           properties a set of properties are to be validated
     *
     * @return          {@code true} if and only if the specified properties set contains all the necessary
     *                  configurations and they are valid i.e. it is possible to start a server using them,
     *                  {@code false} otherwise
     * */
    public static boolean arePropertiesValid(@NotNull Properties properties) {
        if (properties == null) {
            return false;
        }
        try {
            int port = Integer.parseInt(properties.getProperty("port"));
            if (port < 0 || port > 65536) {
                ServerProcessing.LOGGER.error(buildMessage("The port value was expected to be between 0 and 65536"
                        ,"but found", port));
            }
        } catch (NumberFormatException e) {
            ServerProcessing.LOGGER.warn(buildMessage("Unable to extract a port number from server configuration",
                    properties.getProperty("port")));
            return false;
        }
        if (!new File(properties.getProperty("roomsDir")).isDirectory()) {
            ServerProcessing.LOGGER.warn(buildMessage("Invalid roomsDir value was set:", properties.getProperty("roomsDir")));
            return false;
        }
        if (!new File(properties.getProperty("clientsDir")).isDirectory()) {
            ServerProcessing.LOGGER.warn(buildMessage("Invalid clientsDir value was set:", properties.getProperty("clientsDir")));
            return false;
        }
        return true;
    }

    /**
     *   The method creates an instance of {@code Property} and loads the properties from the specified file.
     *  The result is the same as a result of invocation {@code arePropertiesValid()}
     *
     * @param           propertyFile represents an abstract path to the file in which
     *                  properties of a server are set
     *
     * @return          {@code true} if and only if the specified abstract filepath  properties set contains
     *                  all the necessary configurations and they are valid i.e. it is possible
     *                  to start a server using them, {@code false} otherwise
     * */
    public static boolean arePropertiesValid(@NotNull File propertyFile) {
        if (propertyFile == null) {
            return false;
        }
        if(!propertyFile.isFile()) {
            return false;
        }
        Properties properties = new Properties();
        try {
            properties.loadFromXML(new BufferedInputStream(new FileInputStream(propertyFile)));
        } catch (IOException e) {
            ServerProcessing.LOGGER.error(e.getLocalizedMessage());
            return false;
        }
        return arePropertiesValid(properties);
    }

    /**
     *  The method {@code getDefaultProperties} returns
     * the default properties pattern for all servers.
     * */
    static Properties getDefaultProperties() {
        if(ServerProcessing.defaultProperties == null) {
            Properties properties = new Properties();
            // a port number on which the server will be started
            properties.setProperty("port", "5940");
            // a server
            properties.setProperty("server_login", "God");
            properties.setProperty("server_password","change_me");
            // a path to the folder where clients' data will be stored
            properties.setProperty("clientsDir", buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "clients",
                    File.separatorChar, "folder", File.separatorChar, "path")
            );
            // a path to the folder where the rooms' data will be stored
            properties.setProperty("roomsDir", buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "rooms",
                    File.separatorChar, "folder", File.separatorChar, "path")
            );
            // folder for logs
            properties.setProperty("logsDir",buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "logs",
                    File.separatorChar, "folder", File.separatorChar, "path")
            );
            // setting the folder where the server configuration file will be stored
            properties.setProperty("serverConfig",buildMessage("change",
                    File.separatorChar, "the", File.separatorChar, "server",
                    File.separatorChar, "config", File.separatorChar, "path",
                    File.separatorChar, "serverConfig.xml")
            );
            ServerProcessing.defaultProperties = properties;
        }
        return ServerProcessing.defaultProperties;
    }
}