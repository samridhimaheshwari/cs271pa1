package p2p.helpers;

public class Validator {


    public static boolean isValidPort(String input) {
        return isNumeric(input) && isInPortRange(Integer.parseInt(input));
    }

    private static boolean isNumeric(String value) {

        for (int i = 0; i < value.length(); i++) {

            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInPortRange(int port) {
        return port >= 1024 && port <= 65535;
    }

    public static boolean isValidConnect(String input, String port) {
        return input.equals("connect-clients") && Validator.isValidPort(port);
    }
}
