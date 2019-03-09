package server.room.history;

import common.entities.message.Message;
import org.apache.commons.collections4.queue.CircularFifoQueue;

public class MessageHistory {
    private CircularFifoQueue<Message> messageHistory;
    private MessageListener messageListener;
    public MessageHistory(int dimension) {
        messageHistory = new CircularFifoQueue<>(dimension);
    }
    public MessageHistory() {
        this(50);
    }
    public void addMessage(Message message) {
        addMessage(message, true);
    }
    public void addMessage(Message message, boolean notifyClients) {
        messageHistory.add(message);
        if (notifyClients) {
            if (messageListener == null) {
                throw new IllegalStateException("MessageListener has not been set");
            }
            messageListener.newMessage(message);
        }
    }
    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }
}
