package blockchain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;
import p2p.helpers.JSONHelper;
import p2p.helpers.Type;
import p2p.models.Peer;
import yaml.Config;

import java.io.*;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlockchainMaster {


    private List<Peer> connectedClients;
    private Blockchain blockchain;
    private String myIP;
    private ServerSocket listenSocket;
    private int listenPort = 1234;
    private final int MAX_CONNECTIONS = 3;
    private BufferedReader input;
    private Map<Peer, DataOutputStream> peerOutputMap;
    private final Config config;

    public BlockchainMaster(ServerSocket serverSocket) throws IOException {
        config = getConfig();
        blockchain = new Blockchain();
        listenSocket = serverSocket;
        myIP = Inet4Address.getLocalHost().getHostAddress();

        // list of all clients (peers) connected to this host
        connectedClients = new ArrayList<Peer>();

        input = new BufferedReader(new InputStreamReader(System.in));

        // map a peer to an output stream
        peerOutputMap = new HashMap<Peer, DataOutputStream>();
    }

    private void startServer() throws IOException {

        while (true) {
            try {
                Socket connectionSocket = listenSocket.accept();
                // once there is a connection, serve them on thread
                new Thread(new ClientHandler(connectionSocket, blockchain)).start();

            } catch (IOException e) {

            }
        }

    }

    private class ClientHandler implements Runnable {

        private Socket clientSocket;
        private Blockchain blockchain;

        public ClientHandler(Socket socket, Blockchain blockchain) {
            this.clientSocket = socket;
            this.blockchain = blockchain;
        }

        public void run() {

            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                // read all messages sent to the host
                while (true) {
                    String jsonStr = input.readLine();

                    // when the other end of the input stream is closed,
                    // will received null; when null, close thread
                    if (jsonStr == null) {
                        return;
                    }

                    String ip = JSONHelper.parse(jsonStr, "ip");
                    int port = Integer.valueOf(JSONHelper.parse(jsonStr, "port"));

                    // each JSON string received/written can be of 3 types
                    Type type = Type.valueOf(JSONHelper.parse(jsonStr, "type"));
                    String clientId = String.valueOf(config.getProcessIds().get(ip));
                    switch (type) {
                        case CONNECT:
                            displayConnectSuccess(jsonStr);
                            break;
                        case TRANSACT:
                            int amount = Integer.parseInt(JSONHelper.parse(jsonStr, "amount"));
                            String receiver = JSONHelper.parse(jsonStr, "receiver");
                            if(this.blockchain.getBalance(clientId)>=amount){
                                this.blockchain.addBlock(clientId, receiver, amount);
                                displayTransactSuccess(ip);
                            } else{
                                displayTransactionAborted(ip);
                            }
                            break;
                        case BALANCE:
                            displayBalanceMessage(ip, this.blockchain.getBalance(clientId));
                            break;
                        case TERMINATE:
                            displayTerminateMessage(ip, port);
                            terminateConnection(findClient(ip, port));
                            removeClient(findClient(ip, port));
                            input.close();
                            return;
                    }
                }
            } catch (IOException e) {
                System.out.println("Message: Connection drop");
            }
        }
    }

    private void displayTransactSuccess(String ip) {
        Peer peer = null;
        for(Peer p: peerOutputMap.keySet()){
            if(p.getHost().equals(ip)){
                peer = p;
                break;
            }
        }

        sendMessage(peer, generateTransactionSuccessful());
    }

    private void displayTransactionAborted(String ip) {
        Peer peer = null;
        for(Peer p: peerOutputMap.keySet()){
            if(p.getHost().equals(ip)){
                peer = p;
                break;
            }
        }

        sendMessage(peer, generateTransactionAborted());
    }



    private void displayBalanceMessage(String ip, int balance) {
        Peer peer = null;
        for(Peer p: peerOutputMap.keySet()){
            if(p.getHost().equals(ip)){
                peer = p;
                break;
            }
        }

        sendMessage(peer, generateBalanceString(balance));
    }

    private String generateTransactionAborted() {
        return JSONHelper.makeJson(Type.TRANSACT, myIP, listenPort, "TRANSACTION ABORTED DUE TO INSUFFICIENT BALANC").toJSONString();
    }

    private String generateTransactionSuccessful() {
        return JSONHelper.makeJson(Type.TRANSACT, myIP, listenPort, "TRANSACTION SUCCESSFUL").toJSONString();
    }

    private String generateBalanceString(int balance) {
        return JSONHelper.makeJson(Type.BALANCE, myIP, listenPort, balance).toJSONString();
    }

    private void sendMessage(Peer peer, String jsonString) {
        try {
            // "\r\n" so when readLine() is called,
            // it knows when to stop reading
            peerOutputMap.get(peer).writeBytes(jsonString + "\r\n");

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private void displayConnectSuccess(String jsonStr) throws IOException {
        String ip = JSONHelper.parse(jsonStr, "ip");
        int port = Integer.valueOf(JSONHelper.parse(jsonStr, "port"));
        System.out.println("\nPeer [ip: " + ip + ", port: " + port + "] connects to you");
        System.out.print("-> ");
        // save peer's info, used for a lot of other stuff
        Peer peer = new Peer(ip, port);
        connectedClients.add(peer);
        peerOutputMap.put(peer, new DataOutputStream(peer.getSocket().getOutputStream()));
    }

    private Peer findClient(String ip, int port) {
        for (Peer p : connectedClients)
            if (p.getHost().equals(ip) && p.getPort() == port)
                return p;
        return null;
    }

    private void removeClient(Peer peer) {
        connectedClients.remove(peer);
        peerOutputMap.remove(peer);
    }

    private void displayMessage(String ip, int port, String message) {
        System.out.println("\nMessage received from IP: " + ip);
        System.out.println("Sender's Port: " + port);
        System.out.println("Message: " + message);

        // "->" doesn't display after the user receive a
        // message
        System.out.print("-> ");
    }

    private void terminateConnection(Peer peer) {
        try {
            peer.getSocket().close();
            peerOutputMap.get(peer).close();
        } catch (IOException e) {

        }
    }

    private void displayTerminateMessage(String ip, int port) {
        System.out.println();
        System.out.println("Peer [ip: " + ip + " port: " + port + "] has terminated the connection");
        System.out.print("-> ");
    }


    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(1234);
        BlockchainMaster server = new BlockchainMaster(serverSocket);
        server.startServer();
    }

    private Config getConfig() throws FileNotFoundException {
        Yaml yaml = new Yaml();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(classLoader.getResource("config.yaml").getFile());
        InputStream inputStream = new FileInputStream(file);
        Map yamlMap = yaml.load(inputStream);
        ObjectMapper mapper = new ObjectMapper(); // jackson's objectmapper
        return mapper.convertValue(yamlMap, Config.class);
    }
}
