package server.room.history;

import common.entities.message.Message;
import javafx.beans.value.ChangeListener;

public interface MessageListener {
    void newMessage(Message message);
}
