package messenger2;


import messenger2.message.Message;


import javax.xml.bind.*;
import java.io.*;
import java.net.Socket;


public class Utils {
    public static String name;
    public static String password;
    public static int id;
    public static Socket socket;
    public static DataInputStream reader;
    public static DataOutputStream writer;
    public static Marshaller getMarshaller () {
        Marshaller marshaller = null;
        try {
             JAXBContext context = JAXBContext.newInstance(Message.class);
             marshaller = context.createMarshaller();
             marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        } catch (JAXBException ex) {
            System.out.println(ex);
        }
        return  marshaller;
    }
    public static Unmarshaller getUnmarshaller () {
        Unmarshaller unmarshaller= null;
        try {
            JAXBContext context = JAXBContext.newInstance(Message.class);
            unmarshaller = context.createUnmarshaller();
            unmarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        } catch (JAXBException ex) {
            System.out.println(ex);
        }
        return  unmarshaller;
    }
    public static void sendMessenge(Message m) {
        Marshaller marshaller = getMarshaller();
        StringWriter stringWriter = new StringWriter();
        try {
            marshaller.marshal(m, stringWriter);
            String str = stringWriter.toString();
            writer.writeUTF(str);
        } catch(IOException | JAXBException ex) {
            System.out.println(ex);
        }
    }


    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
