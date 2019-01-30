package common.message;

import common.message.status.RequestStatus;
import common.message.status.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;


public class XMLMessageBuilder {
    private Message message;
    private DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    private Document document;
    private TransformerFactory transformerFactory = TransformerFactory.newInstance();
    private String xmlText;

    public XMLMessageBuilder(Message message) {
        if (message == null) {
            throw new NullPointerException("Message must not be null");
        }
        this.message = message;
    }
    public XMLMessageBuilder() {
    }
    public XMLMessageBuilder buildXML() {
        if (message == null) {
            throw new IllegalStateException("Any instance of Message has not been set");
        }
        try {
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            document = documentBuilder.newDocument();
            Element message = document.createElement("message");
            Element type = document.createElement("type");
            type.setTextContent(this.message.getType().toString());
            message.appendChild(type);
            Element status = document.createElement("status");
            status.setTextContent(this.message.getStatus().toString());
            message.appendChild(status);
            Element creationDateTime = document.createElement("creationDateTime");
            creationDateTime.setTextContent(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(this.message.getCreationDateTime()));
            if (this.message.getType() == Type.REQUEST) {
                requestXML();
            } else {
                responseXML();
            }
            document.appendChild(message);
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter stringWriter = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(stringWriter));
            xmlText = stringWriter.toString();
        } catch (ParserConfigurationException | TransformerException e) {
            e.printStackTrace();
        }
        return this;
    }

    private void requestXML() {
        Element fromId = document.createElement("fromId");
        fromId.setTextContent(String.valueOf(message.getFromId()));
        document.getDocumentElement().appendChild(fromId);
        switch ((RequestStatus)(message.getStatus())) {
            case MESSAGE:
                requestMessage();
                break;
            case REGISTRATION:
                requestRegistration();
                break;
            case AUTH:
                requestAuth();
                break;
            case USERBAN:
                requestUserBan();
                break;
            case CREATE_ROOM:
                requestCreateRoom();
                break;
            case DELETE_ROOM:
                requestDeleteRoom();
                break;
            case INVITE_USER:
                requestInviteUser();
                break;
            case UNINVITE_USER:
                requestUninviteUser();
                break;
        }
    }

    private void requestMessage() {
        Element fromId = document.createElement("fromId");
        fromId.setTextContent(String.valueOf(message.getFromId()));
        document.getDocumentElement().appendChild(fromId);
        Element roomId = document.createElement("roomId");
        roomId.setTextContent(String.valueOf(message.getToId()));
        document.getDocumentElement().appendChild(roomId);
        Element msg = document.createElement("msg");
        msg.setTextContent(message.getText());
        document.getDocumentElement().appendChild(msg);
    }

    private void requestRegistration() {
        Element login = document.createElement("login");
        login.setTextContent(message.getLogin());
        document.getDocumentElement().appendChild(login);
        Element password = document.createElement("password");
        password.setTextContent(message.getPassword());
        document.getDocumentElement().appendChild(password);
    }

    private void requestAuth() {
        Element login = document.createElement("login");
        login.setTextContent(message.getLogin());
        document.getDocumentElement().appendChild(login);
        Element password = document.createElement("password");
        password.setTextContent(message.getPassword());
        document.getDocumentElement().appendChild(password);
    }

    private void requestUserBan() {
        Element fromId = document.createElement("fromId");
        fromId.setTextContent(String.valueOf(message.getFromId()));
        document.getDocumentElement().appendChild(fromId);
        Element targetId = document.createElement("targetId");
        targetId.setTextContent(message.getPassword());
        document.getDocumentElement().appendChild(targetId);
    }

    private void requestCreateRoom() {
        Element fromId = document.createElement("fromId");
        fromId.setTextContent(String.valueOf(message.getFromId()));
        document.getDocumentElement().appendChild(fromId);
        Element targetId = document.createElement("targetId");
        targetId.setTextContent(message.getPassword());
        document.getDocumentElement().appendChild(targetId);
    }

    private void requestDeleteRoom() {
        Element fromId = document.createElement("fromId");
        fromId.setTextContent(String.valueOf(message.getFromId()));
        document.getDocumentElement().appendChild(fromId);
        Element targetId = document.createElement("targetId");
        targetId.setTextContent(message.getPassword());
        document.getDocumentElement().appendChild(targetId);
    }

    private void requestInviteUser() {
        Element fromId = document.createElement("fromId");
        fromId.setTextContent(String.valueOf(message.getFromId()));
        document.getDocumentElement().appendChild(fromId);
        Element targetId = document.createElement("targetId");
        targetId.setTextContent(message.getPassword());
        document.getDocumentElement().appendChild(targetId);
        Element roomId = document.createElement("roomId");
        roomId.setTextContent(String.valueOf(message.getRoomId()));
        document.getDocumentElement().appendChild(targetId);
    }

    private void requestUninviteUser() {
        Element fromId = document.createElement("fromId");
        fromId.setTextContent(String.valueOf(message.getFromId()));
        document.getDocumentElement().appendChild(fromId);
        Element targetId = document.createElement("targetId");
        targetId.setTextContent(message.getPassword());
        document.getDocumentElement().appendChild(targetId);
        Element roomId = document.createElement("roomId");
        roomId.setTextContent(String.valueOf(message.getRoomId()));
        document.getDocumentElement().appendChild(targetId);
    }

    private void responseXML() {
        if (message.getText() != null) {
            Element msg = document.createElement("msg");
            msg.setTextContent(message.getText());
        }
    }

    public Message getMessage() {
        return message;
    }

    public XMLMessageBuilder setMessage(Message message) {
        if (message == null) {
            throw new NullPointerException("Message must not be null");
        }
        this.message = message;
        xmlText = null;
        document = null;
        return this;
    }

    public String getXmlText() {
        if (xmlText == null) {
            throw new IllegalStateException("The method buildXml has not been invoked");
        }
        return xmlText;
    }
}