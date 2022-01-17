package helpers;

public class Validator {

    /**
     * determine if the user string input is a valid port. valid port define as:
     * 1024-65535
     */
    public static boolean isValidPort(String input) {
        return isNumeric(input) && isInPortRange(Integer.parseInt(input));
    }

    /**
     * determine if a string value is numeric, contains only digits
     */
    private static boolean isNumeric(String value) {

        for (int i = 0; i < value.length(); i++) {

            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 1024 - 65535 are valid ports
     */
    private static boolean isInPortRange(int port) {
        return port >= 1024 && port <= 65535;
    }

    /**
     * When inputs are splits : args[0] should be "connect" arg[1] should be ip
     * address arg[2] should be a valid port number
     * 
     * @param input
     *            user command line input
     * @return true if user provides logical inputs to make a connection with a
     *         socket
     */
    public static boolean isValidConnect(String input) {
        String[] args = input.split(" ");
        return args.length == 3 && args[0].equals("connect")
                && Validator.isValidPort(args[2]);
    }
}
