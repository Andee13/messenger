package common;

/**
 *  The class {@code Utils} contains mostly static methods are commonly used for supporting operations
 * like message building.
 * */
public class Utils {

    /**
     *  The method {@code buildMessage} handles with building a composite messages. From different variable parts that
     * can differ depending on context.
     *  It does not throw a {@code NullPointerException} if there is {@code null} among them.
     *  It simply appends one part to another separating them by spaces.
     *
     * @param           elements parts of message to be built
     *
     * @return          a {@code String} composed of passed {@code elements}
     * */
    public static String buildMessage(Object...elements) {
        StringBuilder messageBuilder = new StringBuilder();
        for (Object element : elements) {
            messageBuilder.append(element).append(' ');
        }
        return messageBuilder.toString().substring(0, messageBuilder.length() - 1);
    }
}
