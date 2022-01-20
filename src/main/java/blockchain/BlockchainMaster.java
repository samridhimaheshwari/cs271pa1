package blockchain;

import p2p.helpers.JSONHelper;
import p2p.helpers.Type;
import p2p.models.Client;
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


    private List<Client> connectedClients;
    private Blockchain blockchain;
    private String myIP;
    private ServerSocket listenSocket;
    private int listenPort = 1234;
    private final int MAX_CONNECTIONS = 3;
    private BufferedReader input;
    private Map<Client, DataOutputStream> clientOutputMap;
    private final Config config;

    public BlockchainMaster(ServerSocket serverSocket) throws IOException {
        config = CommonUtil.getConfig();
        blockchain = new Blockchain();
        listenSocket = serverSocket;
        myIP = Inet4Address.getLocalHost().getHostAddress();

        connectedClients = new ArrayList<Client>();

        input = new BufferedReader(new InputStreamReader(System.in));

        clientOutputMap = new HashMap<Client, DataOutputStream>();
    }

    private void startServer() throws IOException {

       new Thread(() -> {
        while (true) {
            try {
                Socket connectionSocket = listenSocket.accept();
                new Thread(new ClientHandler(connectionSocket, blockchain)).start();

            } catch (IOException e) {

            }
        }
       }).start();

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

                while (true) {
                    String jsonStr = input.readLine();


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
                        case TRANSACTION:
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
                            Client peer = findClient(ip, port);
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
        Client peer = findPeer(ip);
        if (peer != null) CommonUtil.sendMessage(clientOutputMap.get(peer), generateTransactionSuccessful());

    }

    private void displayTransactionAborted(String ip) {
        Client peer = findPeer(ip);
        CommonUtil.sendMessage(clientOutputMap.get(peer), generateTransactionAborted());
    }

    private void displayBalanceMessage(String ip, int balance) {
        Client peer = findPeer(ip);
        CommonUtil.sendMessage(clientOutputMap.get(peer), generateBalanceString(balance));
    }

    private Client findPeer(String ip) {
        Client peer = null;
        for(Client p: clientOutputMap.keySet()){
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
        Client peer = new Client(ip, port);
        connectedClients.add(peer);
        clientOutputMap.put(peer, new DataOutputStream(peer.getSocket().getOutputStream()));
    }

    private Client findClient(String ip, int port) {
        for (Client p : connectedClients)
            if (p.getHost().equals(ip) && p.getPort() == port)
                return p;
        return null;
    }

    private void removeClient(Client peer) {
        connectedClients.remove(peer);
        clientOutputMap.remove(peer);
    }

    public void acceptInputs() throws IOException {
        System.out.println("Blockchain server");

        while (true) {
            System.out.print("-> ");
            String choice = input.readLine();
            String option = choice.split(" ")[0].toLowerCase();

            switch (option) {
                case "myip":
                    System.out.println("My IP Address: " + myIP);
                    break;
                case "myport":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        System.out.println("Listening on port: " + listenPort);
                    break;
                case "list":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        displayList();
                    break;
                case "show-blockchain":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        displayBlockchain();
                    break;

                default:
                    System.out.println("not a recognized command");
            }
        }
    }



    private void displayList() {
        if (connectedClients.isEmpty())
            System.out.println("No client connected.");
        else {
            System.out.println("id:   IP Address     Port No.");
            for (int i = 0; i < connectedClients.size(); i++) {
                Client peer = connectedClients.get(i);
                System.out.println((i + 1) + "    " + peer.getHost() + "     " + peer.getPort());
            }
            System.out.println("Total clients: " + connectedClients.size());
        }
    }

    private void displayBlockchain() {
        System.out.println("Now displaying blockchain");
        this.blockchain.print();
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(1234);
        BlockchainMaster server = new BlockchainMaster(serverSocket);
        server.startServer();
        server.acceptInputs();
    }
}
