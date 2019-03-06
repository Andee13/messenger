package messenger2;

import messenger2.message.Message;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.StringReader;
import static  messenger2.Utils.*;

public class ReaderThread extends Thread{
    public int time = 0;
    public Message message;

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted() && time < 2000){


                try {
                    if(reader.available() > 1) {
                        String stringXML = reader.readUTF();

                        StringReader stringReader = new StringReader(stringXML);
                        message = (Message) Utils.getUnmarshaller().unmarshal(stringReader);
                        System.out.println("45"+message);
                    }
                    time = time + 100;
                    if(time >= 2000 || message != null){
                        Thread.currentThread().interrupt();

                    } else {
                        Thread.sleep(100);
                    }
                } catch (IOException | JAXBException | InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    System.out.println(ex);
                }
        }
    }

}
