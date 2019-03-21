package messenger2;

import messenger2.message.Message;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import static messenger2.Utils.*;

public class Waiter extends Thread {
    public static ConcurrentLinkedQueue<Message> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void run(){
        while (true){
            try {
                Message message;
                if(reader.available() > 0) {
                    String stringXML = reader.readUTF();

                    StringReader stringReader = new StringReader(stringXML);
                    message = (Message) Utils.getUnmarshaller().unmarshal(stringReader);
                    queue.add(message);

                    System.out.println("45"+message);
                }
            } catch (IOException | JAXBException ex) {

                System.out.println(ex);
            }
        }
    }
}
