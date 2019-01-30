package common.message;

import common.message.status.RequestStatus;
import common.message.status.ResponseStatus;
import common.message.status.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.IllegalCharsetNameException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class XMLMessageParser {
    private Document document;
    private Message message;
    private DocumentBuilder documentBuilder;
    private String xmlText;

    {
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
    }

    public XMLMessageParser(String xmlText) {
        if(xmlText == null) {
            throw new NullPointerException("Source text must not be null");
        }
        this.xmlText = xmlText;
    }

    public  XMLMessageParser() {
    }

    public XMLMessageParser parseInput() throws SAXException, IOException {
        if(xmlText == null) {
            throw new IllegalStateException("Source text must be set");
        }
        document = documentBuilder.parse(new InputSource(new StringReader(xmlText)));
        Type type = Type.valueOf(document.getDocumentElement()
                .getElementsByTagName("type").item(0).getTextContent());
        if(type == Type.REQUEST) {
            message = new Message(RequestStatus.valueOf(document.getDocumentElement().
                    getElementsByTagName("status").item(0).getTextContent()));
            setMessageFields();
        } else {
            message = new Message(ResponseStatus.valueOf(document.getDocumentElement()
                    .getElementsByTagName("status").item(0).getTextContent()));
            setMessageFields();
        }
        return this;
    }

    private void setMessageFields() {
        Element message = document.getDocumentElement();
        NodeList nodeList = message.getChildNodes();
        Element element;
        for (int i = 0; i < nodeList.getLength(); i++) {
            if(nodeList.item(i).getNodeType() != Node.ELEMENT_NODE ||
                    "type".equals(((Element) (nodeList.item(i))).getTagName()) ||
                    "status".equals(((Element) (nodeList.item(i))).getTagName())) {
                continue;
            }
            element = (Element) nodeList.item(i);
            switch (element.getNodeName()) {
                case "fromId" :
                    this.message.setFromId(nodeList.item(i).getTextContent());
                    break;
                case "toId" :
                    this.message.setToId(nodeList.item(i).getTextContent());
                    break;
                case "roomId" :
                    this.message.setRoomId(nodeList.item(i).getTextContent());
                    break;
                case "login" :
                    this.message.setLogin(nodeList.item(i).getTextContent());
                    break;
                case "password" :
                    this.message.setPassword(nodeList.item(i).getTextContent());
                    break;
                case "creationDateTime" :
                    this.message.setCreationDateTime(LocalDateTime.from(
                            DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(nodeList.item(i).getTextContent())));
                    break;
                case "text" :
                    this.message.setText(nodeList.item(i).getTextContent());
                    break;
                    default:
                        throw new IllegalCharsetNameException(new StringBuilder("Tag name:").append(nodeList.item(i)
                                .getLocalName())
                                .append(" does not match any field of ").append(Message.class.getName()).toString());
            }
        }
    }

    public Message getMessage() {
        if(message == null) {
            throw new IllegalStateException("Method parseInput has not been invoked");
        }
        return message;
    }
}