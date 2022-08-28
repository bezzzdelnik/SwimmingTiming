package util;

public class CommonUtils {

    public static boolean stringIsNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static boolean portIsValid(String port) {
        return Constants.PORT_PATTERN.matcher(port).matches();
    }

    public static boolean textIsInteger(String text) {
        return Constants.INTEGER.matcher(text).matches();
    }
}
