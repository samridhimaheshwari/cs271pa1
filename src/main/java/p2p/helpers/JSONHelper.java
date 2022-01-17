package p2p.helpers;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class JSONHelper {

    /**
     * @param jsonString
     *            a Stringify JSON object
     * @param key
     *            the JSON property name, key, that you want to retrive the
     *            value of
     * @return the value corresponding the to key in a String
     */
    public static String parse(String jsonString, String key) {
        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObject = (JSONObject) parser.parse(jsonString);
            return jsonObject.get(key).toString();
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * This is an overloaded method
     * 
     * @param type
     *            the the purpose of this JSONO object (MESSAGE, CONNECT,
     *            TERMINATE)
     * @param ip
     *            client's ip address
     * @param port
     *            client's port number
     * @param message
     *            the message you want to send to another client
     * @return wrap all the parameters entered into a JSON object
     */
    @SuppressWarnings("unchecked")
    public static JSONObject makeJson(Type type, String ip, int port,
            String message) {
        JSONObject jsonObject = makeJson(type, ip, port);
        jsonObject.put("message", message);
        return jsonObject;
    }

    public static JSONObject makeJson(Type type, String ip, int port,
                                      int balance) {
        JSONObject jsonObject = makeJson(type, ip, port);
        jsonObject.put("balance", balance);
        return jsonObject;
    }


    public static JSONObject makeJson(Type type, String ip, int port) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("type", type.name());
        jsonObject.put("ip", ip);
        jsonObject.put("port", port);
        return jsonObject;
    }
}
