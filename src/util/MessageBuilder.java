package util;

public class MessageBuilder {

    public static String message(String message, String... args) {
        for (String arg : args) {
            message = message.replace("{}", arg);
        }
        return message;
    }
}
