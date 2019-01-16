package ua.sumdu.j2se.Birintsev.common;

import javafx.scene.image.Image;
import ua.sumdu.j2se.Birintsev.common.sendable.Sendable;

import java.io.File;

public class Message implements Sendable {
    private String text;
    private Image image;
    private File file;

    public Message(String text) {
        this.text = text;
    }

    public Message(Image image) {
        this.image = image;
    }

    public Message(File file) {
        this.file = file;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
