package common.entities;

/**
 * The class Shell is written in order to provide an ability to operate with common resources safely.
 * */
public class Shell <T> {
    private volatile T item;

    public Shell(T item) {
        this.item = item;
    }

    public Shell() {
    }

    /**
     * Provides a thread-safe access to the encapsulated item
     * */
    public T safe() {
        //noinspection SynchronizeOnNonFinalField
        synchronized (item) {
            return item;
        }
    }
}