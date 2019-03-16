package messenger2;

import messenger2.message.Message;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ConcurrentHashMap;
import static messenger2.Utils.*;

public class Waiter extends Thread {
    //public static ConcurrentHashMap<,>

    @Override
    public void run(){
        while (true){
            try {
                Message message;
                if(reader.available() > 1) {
                    String stringXML = reader.readUTF();

                    StringReader stringReader = new StringReader(stringXML);
                    message = (Message) Utils.getUnmarshaller().unmarshal(stringReader);
                    System.out.println("45"+message);
                }
            } catch (IOException | JAXBException ex) {

                System.out.println(ex);
            }
        }
    }
}
