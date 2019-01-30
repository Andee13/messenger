package server;

import common.message.Message;
import common.message.XMLMessageBuilder;
import common.message.XMLMessageParser;
import common.message.status.ResponseStatus;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException, XMLStreamException {
        /*DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
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
        Document document = documentBuilder.parse("src/server.xml");
        Element root = document.getDocumentElement();
        System.out.println(root.getTextContent());*/
        /*Message message = new Message(RequestStatus.AUTH).setLogin("leader228228").setPassword("12345");
        XMLMessageBuilder xmlMessageBuilder = new XMLMessageBuilder(message);

        Message message1 = new Message(RequestStatus.REGISTRATION).setLogin("leader228228").setPassword("12345");
        xmlMessageBuilder = new XMLMessageBuilder(message1);

        Message message2 = new Message(ResponseStatus.ERROR).setText(new IllegalStateException("some test info").getText());
        xmlMessageBuilder = new XMLMessageBuilder(message2);

        Message message3 = new Message(ResponseStatus.ACCEPTED);
        xmlMessageBuilder = new XMLMessageBuilder(message3);*/

        /*Message message = new Message(ResponseStatus.ERROR).setException(new NullPointerException("some info"));
        XMLMessageBuilder xmlMessageBuilder = new XMLMessageBuilder(message);
        xmlMessageBuilder.buildXML();
        String xml = xmlMessageBuilder.getXmlText();
        XMLMessageParser xmlMessageParser = new XMLMessageParser(xml);
        xmlMessageParser.parseInput();
        Message message1 = xmlMessageParser.getMessage();
        System.out.println(message1);*/
        /*String s1 = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append("<!DOCTYPE server [\n").append("        <!ELEMENT server (port)>\n").append("        <!ELEMENT port ANY>\n").append("        ]>\n").append("<server>\n").append("    <port>5940</port>\n").append("</server>").toString();
        String s2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE server [\n" +
                "        <!ELEMENT server (port)>\n" +
                "        <!ELEMENT port ANY>\n" +
                "        ]>\n" +
                "<server>\n" +
                "    <port>5940</port>\n" +
                "</server>";
        System.out.println(s1.equals(s2));*/

        /*XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLStreamReader xmlStreamReader = xmlInputFactory.createXMLStreamReader(
                new BufferedReader(new FileReader("D:\\Java projects\\Messenger\\messenger\\server\\src\\main\\res\\xml\\server.xml")));
        while (xmlStreamReader.hasNext()){
            int event = xmlStreamReader.next();
            if(event == XMLStreamConstants.START_ELEMENT && xmlStreamReader.getLocalName().equals("port")){
                xmlStreamReader.next();
                System.out.println(xmlStreamReader.getText());
            }
        }*/

    }
}
