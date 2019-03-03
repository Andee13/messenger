package messenger2;

import messenger2.message.Message;

import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Utils {
    public static Socket socket;
    public static Scanner reader;
    public static PrintWriter writer;
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
}
