package blockchain;

import p2p.helpers.JSONHelper;
import p2p.helpers.Type;
import p2p.models.Peer;
import util.CommonUtil;
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
    private Map<Peer, DataOutputStream> clientOutputMap;
    private final Config config;

    public BlockchainMaster(ServerSocket serverSocket) throws IOException {
        config = CommonUtil.getConfig();
        blockchain = new Blockchain();
        listenSocket = serverSocket;
        myIP = Inet4Address.getLocalHost().getHostAddress();

        // list of all clients (peers) connected to this host
        connectedClients = new ArrayList<Peer>();

        input = new BufferedReader(new InputStreamReader(System.in));

        // map a peer to an output stream
        clientOutputMap = new HashMap<Peer, DataOutputStream>();
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
                            CommonUtil.displayConnectSuccess(jsonStr);
                            addClient(ip, port);
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
                            Peer peer = findClient(ip, port);
                            if (peer!=null) {
                                CommonUtil.displayTerminateMessage(ip, port);
                                CommonUtil.terminateConnection(peer.getSocket(), clientOutputMap.get(peer));
                                removeClient(peer);
                                input.close();
                            }
                            return;
                    }
                }
            } catch (IOException e) {
                System.out.println("Message: Connection drop");
            }
        }
    }

    private void displayTransactSuccess(String ip) {
        Peer peer = findPeer(ip);
        if (peer != null) CommonUtil.sendMessage(clientOutputMap.get(peer), generateTransactionSuccessful());

    }

    private void displayTransactionAborted(String ip) {
        Peer peer = findPeer(ip);
        CommonUtil.sendMessage(clientOutputMap.get(peer), generateTransactionAborted());
    }

    private void displayBalanceMessage(String ip, int balance) {
        Peer peer = findPeer(ip);
        CommonUtil.sendMessage(clientOutputMap.get(peer), generateBalanceString(balance));
    }

    private Peer findPeer(String ip) {
        Peer peer = null;
        for(Peer p: clientOutputMap.keySet()){
            if(p.getHost().equals(ip)){
                peer = p;
                break;
            }
        }
        return peer;
    }

    private String generateTransactionAborted() {
        return JSONHelper.makeJson(Type.MESSAGE, myIP, listenPort, "TRANSACTION ABORTED DUE TO INSUFFICIENT BALANC").toJSONString();
    }

    private String generateTransactionSuccessful() {
        return JSONHelper.makeJson(Type.MESSAGE, myIP, listenPort, "TRANSACTION SUCCESSFUL").toJSONString();
    }

    private String generateBalanceString(int balance) {
        return JSONHelper.makeJson(Type.MESSAGE, myIP, listenPort, "Balance is " + balance).toJSONString();
    }

    private void addClient(String ip, int port) throws IOException {
        Peer peer = new Peer(ip, port);
        connectedClients.add(peer);
        clientOutputMap.put(peer, new DataOutputStream(peer.getSocket().getOutputStream()));
    }

    private Peer findClient(String ip, int port) {
        for (Peer p : connectedClients)
            if (p.getHost().equals(ip) && p.getPort() == port)
                return p;
        return null;
    }

    private void removeClient(Peer peer) {
        connectedClients.remove(peer);
        clientOutputMap.remove(peer);
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(1234);
        BlockchainMaster server = new BlockchainMaster(serverSocket);
        server.startServer();
    }
}
