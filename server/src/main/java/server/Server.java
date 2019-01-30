package server;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

public class Server extends Thread {
    private int port;
    private List<ClientListener> clients;
    private static File usersDir;

    public static void main(String[] args) {
        Server server = new Server();
        usersDir = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("users").toString());
        // TODO logging about directory creation
        if(!usersDir.exists() && usersDir.mkdir()){

        }
        File config = new File(new StringBuilder(
                Server.class.getProtectionDomain().getCodeSource().getLocation().getPath())
                .append("server.cfg").toString());
        if(!config.exists()){
            try {
                config.createNewFile();
                (new BufferedWriter(new FileWriter(config)))
                        .write(new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                        .append("<!DOCTYPE server [\n")
                        .append("        <!ELEMENT server (port)>\n")
                        .append("        <!ELEMENT port ANY>\n")
                        .append("        ]>\n")
                        .append("<server>\n")
                        .append("    <port>5940</port>\n")
                        .append("</server>")
                        .toString());
                // TODO create a tray notification
                System.out.println((new StringBuilder("Please, set the server parameters in the file ")
                        .append(config.getAbsolutePath()).append(" and restart the server")));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        // TODO logging the exceptions
        try {
            server.loadConfiguration(new FileInputStream(config));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        server.start();
    }

    // TODO logging the exceptions
    @Override
    public void run() {
        ServerSocket serverSocket = null;
        Socket socket = null;
        while (true) {
            try {
                clients.add(new ClientListener(this, serverSocket.accept()));
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
        }

    }

    // TODO logging exceptions
    public void close() {
        for (ClientListener client : clients) {
            try {
                client.getSocket().close();
                clients.remove(client);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        interrupt();
    }

    private void loadConfiguration(InputStream is)
            throws SAXException, IOException, ParserConfigurationException{
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setValidating(true);
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        documentBuilder.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });

        Document document = documentBuilder.parse(new BufferedInputStream(is));
        Element root = document.getDocumentElement();
        port = Integer.parseInt(root.getElementsByTagName("port").item(0).getTextContent());
    }

    public void closeClientSession (ClientListener client) throws IOException {
        client.getIn().close();
        client.getOut().close();
        client.getSocket().close();
        clients.remove(client);
        client.interrupt();
    }

    public static File getUsersDir() {
        return usersDir;
    }


}
