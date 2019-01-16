package ua.sumdu.j2se.Birintsev.common.utill;

import javafx.scene.Node;

public class Utill {
    public static final String ipV4Pattern = "(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[0-9]{2}|[0-9])(\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[0-9]{2}|[0-9])){3}";
    public static final String IpV4WithPortPattern = new StringBuilder(ipV4Pattern).append(":(\\d{1,5})").toString();

    /**
     * Hides the specified nodes of a scene
     * */
    public static void hideNodes(Node...nodes) {
        for (Node node : nodes){
            node.setVisible(false);
        }
    }

    /**
     * Showes the specified nodes of a scene
     * */
    public static void showNodes(Node...nodes) {
        for (Node node : nodes){
            node.setVisible(true);
        }
    }

}
