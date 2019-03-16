package server.room.history;

import common.entities.message.Message;

public interface MessageListener {
    void newMessage(Message message);
}
