package common.entities;

/**
 * The class Shell is written in order to provide an ability to operate with common resources safely.
 * */
public class Shell <T>{
    private volatile T item;
    public Shell(T item) {
        this.item = item;
    }
    public Shell(){
    }
    public T safe(){
        synchronized (item) {
            return item;
        }
    }
}