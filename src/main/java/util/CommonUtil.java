package util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;
import p2p.helpers.JSONHelper;
import yaml.Config;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class CommonUtil {

    /**
     * notify the user that a peer has drop the connection.
     *
     * @param
     */
    public static void displayTerminateMessage(String ip, int port) {
        System.out.println();
        System.out.println("Peer [ip: " + ip + " port: " + port + "] has terminated the connection");
    }

    /**
     * display a notification that a client has connected to the host. create a
     * peer object, with peer's connection info, and add them to connectedPeers.
     * also add the peer to a map with an output stream object.
     *
     * @param jsonStr Stringify JSON object
     * @throws IOException
     */
     public static void displayConnectSuccess(String jsonStr) throws IOException {
        String ip = JSONHelper.parse(jsonStr, "ip");
        int port = Integer.valueOf(JSONHelper.parse(jsonStr, "port"));
        System.out.println("\nPeer [ip: " + ip + ", port: " + port + "] connects to you");
    }

    /**
     * display message received from peer along with their port and IP address
     *
     * @param ip
     * @param port
     * @param message
     */
    public static void displayMessage(String ip, int port, String message) {
        System.out.println("\nMessage received from IP: " + ip);
        System.out.println("Sender's Port: " + port);
        System.out.println("Message: " + message);

        // "->" doesn't display after the user receive a
        // message
    }


    // find the client on host's list (connectedPeers)
    // and write to them a message
    public static void sendMessage(DataOutputStream stream, String jsonString) {
        try {
            // "\r\n" so when readLine() is called,
            // it knows when to stop reading
            stream.writeBytes(jsonString + "\r\n");

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static void terminateConnection(Socket socket, DataOutputStream outputStream) {
        try {
            socket.close();
            outputStream.close();
        } catch (IOException e) {

        }
    }



    public static Config getConfig() throws FileNotFoundException {
        Yaml yaml = new Yaml();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(classLoader.getResource("config.yaml").getFile());
        InputStream inputStream = new FileInputStream(file);
        Map yamlMap = yaml.load(inputStream);
        ObjectMapper mapper = new ObjectMapper(); // jackson's objectmapper
        return mapper.convertValue(yamlMap, Config.class);
    }

}
