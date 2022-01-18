package p2p.app;

import java.io.*;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import model.LamportClock;
import model.Message;
import model.Request;
import p2p.helpers.JSONHelper;
import p2p.helpers.Type;
import p2p.helpers.Validator;
import p2p.models.Client;
import p2p.models.Server;
import util.CommonUtil;
import yaml.Config;

public class PeerService {

    private List<Client> connectedClients;
    private Integer listenPort;
    private String myIP;
    private ServerSocket listenSocket;
    private final int MAX_CONNECTIONS = 3;
    private final int WAIT = 3000;
    private BufferedReader input;
    private Map<Client, DataOutputStream> peerOutputMap;
    private Server server;
    private final Config config;
    private static Integer count = 0;
    private Map<String, Message> requests;
    private Map<String, ArrayList<Client>> replies;
    private PriorityQueue<LamportClock> pq;
    private LamportClock lamportClock;


    public PeerService() throws IOException {
        myIP = Inet4Address.getLocalHost().getHostAddress();
        config = CommonUtil.getConfig();
        pq = new PriorityQueue<>(new Comparator<LamportClock>() {
            @Override
            public int compare(LamportClock a, LamportClock b) {
                if(a.getClock() < b.getClock()){
                    return -1;
                } else if(a.getClock() > b.getClock()){
                    return 1;
                } else{
                    if(a.getProcessId()<b.getProcessId()){
                        return -1;
                    } else{
                        return 1;
                    }
                }
            }
        });
        requests = new HashMap();
        replies = new HashMap();
        Integer processId = 0;
        for(Map.Entry<String, Integer> entry: config.getProcessIds().entrySet()) {
            if (myIP.equals(entry.getKey())) {
                processId = entry.getValue();
            }
        }

        lamportClock = new LamportClock(processId);


        // list of all clients (peers) connected to this host
        connectedClients = new ArrayList<Client>();

        input = new BufferedReader(new InputStreamReader(System.in));

        // map a peer to an output stream
        peerOutputMap = new HashMap<Client, DataOutputStream>();


    }


    private void startServer() throws IOException {

        // "serve" each client (peer) on a separate thread
        new Thread(() -> {
            while (true) {
                try {
                    // wait for a peer to connect
                    Socket connectionSocket = listenSocket.accept();

                    // once there is a connection, serve them on thread
                    new Thread(new PeerHandler(connectionSocket)).start();

                } catch (IOException e) {

                }
            }
        }).start();
    }

    // open an IO stream for each peer connected to the host
    // display all the messages send to the host by that peer
    public class PeerHandler implements Runnable {

        private Socket peerSocket;
        private final Object lock = new Object();


        public PeerHandler(Socket socket) {
            this.peerSocket = socket;
        }

        public void run() {

            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));

                // read all messages sent to the host
                while (true) {
                    String jsonStr = input.readLine();

                    // when the other end of the input stream is closed,
                    // will received null; when null, close thread
                    if (jsonStr == null) {
                        return;
                    }

                    String ip = JSONHelper.parse(jsonStr, "ip");
                    int port = Integer.parseInt(JSONHelper.parse(jsonStr, "port"));
                    Message m;
                    LamportClock head;
                    String requestString;
                    ObjectMapper mapper = new ObjectMapper();
                    Request request;

                    // each JSON string received/written can be of 3 types
                    Type type = Type.valueOf(JSONHelper.parse(jsonStr, "type"));
                    switch (type) {
                        case CONNECT:
                            CommonUtil.displayConnectSuccess(jsonStr);
                            addPeer(ip, port);
                            break;
                        case MESSAGE:
                            String message = JSONHelper.parse(jsonStr, "message");
                            CommonUtil.displayMessage(ip, port, message);
                            lamportClock.setClock(lamportClock.getClock() + 1);
                            Thread.sleep(WAIT);
                            pq.poll();
                            lamportClock.setClock(lamportClock.getClock() + 1);
                            sendReleaseMessageToPeers();
                            if(!pq.isEmpty()) {
                                head = pq.peek();
                                if (head != null) {
                                    m = requests.get(getIdFromClock(head));
                                    if (head.getProcessId() == lamportClock.getProcessId() && replies.get(getIdFromClock(head)).size() == connectedClients.size()) {
                                        lamportClock.setClock(lamportClock.getClock() + 1);
                                        if (m.getType() == Type.BALANCE) {
                                            //
                                            CommonUtil.sendMessage(server.getDataOutputStream(), generateBalanceJson());
                                        } else if (m.getType() == Type.TRANSACTION) {
                                            //
                                            CommonUtil.sendMessage(server.getDataOutputStream(), generateTransactionJson(m.getAmount(), m.getReceiver()));
                                        }
                                    }
                                }
                            }

                            break;
                        case REPLY:
                                requestString = JSONHelper.parse(jsonStr, "request");
                                request = mapper.readValue(requestString, Request.class);
                                lamportClock.setClock(lamportClock.getClock() + 1);
                                replies.get(getIdFromClock(request.getLamportClock())).add(findPeer(ip, port));
                                System.out.println("Received Reply from: " + config.getProcessIds().get(ip));
                                if (!pq.isEmpty()) {
                                    head = pq.peek();
                                    if (head.getProcessId() == lamportClock.getProcessId() && replies.get(getIdFromClock(head)).size() == connectedClients.size()) {
                                        lamportClock.setClock(lamportClock.getClock() + 1);
                                        if (requests.get(getIdFromClock(head)).getType() == Type.BALANCE) {
                                            //
                                            CommonUtil.sendMessage(server.getDataOutputStream(), generateBalanceJson());
                                        } else if (requests.get(getIdFromClock(head)).getType() == Type.TRANSACTION) {
                                            Message msg = requests.get(getIdFromClock(head));
                                            //
                                            CommonUtil.sendMessage(server.getDataOutputStream(), generateTransactionJson(msg.getAmount(), msg.getReceiver()));
                                        }
                                    }
                                }
                            break;
                        case REQUEST:
                                requestString = JSONHelper.parse(jsonStr, "request");
                                request = mapper.readValue(requestString, Request.class);
                                Thread.sleep(WAIT);
                                lamportClock.setClock(lamportClock.getClock() + 1);
                                pq.add(new LamportClock(request.getLamportClock().getClock(), request.getLamportClock().getProcessId()));
                                Client peer = findPeer(ip, port);
                                System.out.println("Received Request from: " + config.getProcessIds().get(ip));
                                //
                                sendReplyToPeer(request, peer);
                            break;
                        case RELEASE:
                                pq.poll();

                                lamportClock.setClock(lamportClock.getClock() + 1);
                                System.out.println("Received Release from: " + config.getProcessIds().get(ip));
                            if(!pq.isEmpty()) {
                                head = pq.peek();
                                if (head != null) {
                                    m = requests.get(getIdFromClock(head));
                                    if (head.getProcessId() == lamportClock.getProcessId() && replies.get(getIdFromClock(head)).size() == connectedClients.size()) {
                                        lamportClock.setClock(lamportClock.getClock() + 1);
                                        if (m.getType() == Type.BALANCE) {
                                            //
                                            CommonUtil.sendMessage(server.getDataOutputStream(), generateBalanceJson());
                                        } else if (m.getType() == Type.TRANSACTION) {
                                            //
                                            CommonUtil.sendMessage(server.getDataOutputStream(), generateTransactionJson(m.getAmount(), m.getReceiver()));
                                        }
                                    }
                                }
                            }
                            break;
                        case TERMINATE:
                            peer = findPeer(ip, port);
                            CommonUtil.displayTerminateMessage(ip, port);
                            CommonUtil.terminateConnection(peer.getSocket(), peerOutputMap.get(peer));
                            removePeer(findPeer(ip, port));
                            input.close();
                            return;
                    }
                }
            } catch (IOException e) {
                System.out.println("Message: Connection drop");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendReplyToPeer(Request request, Client peer) throws JsonProcessingException, InterruptedException {
        ObjectMapper objectMapper = new ObjectMapper();
        String message = JSONHelper.makeRequestJson(Type.REPLY, myIP, listenPort, objectMapper.writeValueAsString(request)).toJSONString();
        System.out.println("Sent Reply Message to Client: " + config.getProcessIds().get(peer.getHost()));
        CommonUtil.sendMessage(peerOutputMap.get(peer), message);
    }

    // accept commands from the users
    public void acceptInputs() throws IOException, InterruptedException {
        System.out.println("Welcome to the Blockchain");

        while (true) {
            System.out.print("-> ");
            String choice = input.readLine();
            // the first argument is the command
            String option = choice.split(" ")[0].toLowerCase();

            switch (option) {
                case "start":
                    if (listenSocket == null)
                        initPeer(choice);
                    else
                        System.out.println("Error: you can only listen to one port at a time");
                    break;
                case "myip":
                    System.out.println("My IP Address: " + myIP);
                    break;
                case "myport":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        System.out.println("Listening on port: " + listenPort);
                    break;
                case "connect-server":
                        processServerConnect();
                    break;
                case "connect-clients":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        processClientsConnect(choice, config);
                    break;
                case "list":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        displayList();
                    break;
                case "send":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        processSend(choice);
                    break;
                case "balance":
                case "transaction":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        Thread.sleep(WAIT);
                        initRequest(choice);
                    break;
                case "terminate":
                    if (listenSocket == null)
                        System.out.println("Error: you are not connected");
                    else
                        processTerminate(choice);
                    break;
                case "exit":
                    breakPeerConnections();
                    System.exit(0);
                    break;
                default:
                    System.out.println("not a recognized command");
            }
        }
    }



    // display the list of peers that are connected to the host
    private void displayList() {
        if (connectedClients.isEmpty())
            System.out.println("No peers connected.");
        else {
            System.out.println("id:   IP Address     Port No.");
            for (int i = 0; i < connectedClients.size(); i++) {
                Client peer = connectedClients.get(i);
                System.out.println((i + 1) + "    " + peer.getHost() + "     " + peer.getPort());
            }
            System.out.println("Total Peers: " + connectedClients.size());
        }
    }


    // create a socket, connect to peer
    private void connect(String ip, int port, boolean isServerConnection) throws IOException {

        int attempts = 0;
        final int MAX_ATTEMPTS = 5;
        final int SLEEP_TIME = 1000;
        Socket peerSocket = null;

        // try to connect but will stop after MAX_ATTEMPTS
        do {
            try {
                peerSocket = new Socket(ip, port);
            } catch (IOException e) {

                System.out.println("*** connection failed...attempt: " + (++attempts) + " ***");
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e1) {

                }
            }
        } while (peerSocket == null && attempts < MAX_ATTEMPTS);

        // add (save) the socket so they can be use later
        if (attempts >= MAX_ATTEMPTS) {
            System.out.println("connection was unsuccessful, please try again later");
        } else {
            System.out.println("connected to " + ip + " " + port);
            Client peer = new Client(ip, port);
            connectedClients.add(peer);

            // map this peer to an output stream

            peerOutputMap.put(peer, new DataOutputStream(peerSocket.getOutputStream()));

            // tell the peer your host address and port number
            // tell the peer to connect to you
            CommonUtil.sendMessage(peerOutputMap.get(peer), generateConnectJson());
        }
    }


    private void breakPeerConnections() throws IOException {

        // terminate each peer connection; notify them
        for (Client peer : connectedClients) {
            CommonUtil.sendMessage(peerOutputMap.get(peer), generateTerminateJson());
            CommonUtil.terminateConnection(peer.getSocket(), peerOutputMap.get(peer));
        }

        // close each output stream
        for (Entry<Client, DataOutputStream> e : peerOutputMap.entrySet()) {
            e.getValue().close();
        }

        listenSocket.close();
        System.out.println("chat client close, good bye");
    }

    private void removePeer(Client peer) {
        connectedClients.remove(peer);
        peerOutputMap.remove(peer);
    }

    private String generateConnectJson() {
        return JSONHelper.makeJson(Type.CONNECT, myIP, listenPort).toJSONString();
    }


    private String generateMessageJson(String message) {
        return JSONHelper.makeJson(Type.MESSAGE, myIP, listenPort, message).toJSONString();
    }

    private String generateBalanceJson() {
        return JSONHelper.makeJson(Type.BALANCE, myIP, listenPort).toJSONString();
    }

    private String generateTransactionJson(int amount, String receiver) {
        return JSONHelper.makeJson(Type.TRANSACTION, myIP, listenPort, amount, receiver).toJSONString();
    }

    private String generateTerminateJson() {
        return JSONHelper.makeJson(Type.TERMINATE, myIP, listenPort).toJSONString();
    }

    // id = index, thus 0 to size() - 1
    private boolean isValidPeer(int id) {
        return id >= 0 && id < connectedClients.size();
    }


    private Client findPeer(String ip, int port) {
        for (Client p : connectedClients)
            if (p.getHost().equals(ip) && p.getPort() == port)
                return p;
        return null;
    }


    private void addPeer(String ip, int port) throws IOException {
        Client peer = new Client(ip, port);
        connectedClients.add(peer);
        peerOutputMap.put(peer, new DataOutputStream(peer.getSocket().getOutputStream()));
    }


    private void processClientsConnect(String userInput, Config config) throws IOException {
        HashMap<String, String> peers = config.getPeers();

        for (Map.Entry<String, String> map: peers.entrySet()) {
            String ip = map.getKey();
            String portString = map.getValue();
            Integer port = Integer.parseInt(portString);


            // check if the user input is "valid"
            if (!Validator.isValidConnect(userInput, portString)) {
                System.out.println("connect fail: invalid arguments");
                return;
            }

            // check if connection limited is exceeded
            if (connectedClients.size() >= MAX_CONNECTIONS) {
                System.out.println("connect fail: max connection");
                return;
            }

            // check for self/duplicate connections
            if (!isUniqueConnection(ip, port)) {
                System.out.println("connect fail: no self or duplicate connection");
                return;
            }

            // all tests passed, connect to the peer
            connect(ip, port, false);
        }
    }

    private void processServerConnect() throws IOException {

        // all tests passed, connect to the peer
        //get IP and PORT from config
        String ip = config.getServerIp();
        Integer port = config.getServerPort();
        int attempts = 0;
        final int MAX_ATTEMPTS = 5;
        final int SLEEP_TIME = 1000;
        Socket serverSocket = null;

        // try to connect but will stop after MAX_ATTEMPTS
        do {
            try {
                serverSocket = new Socket(config.getServerIp(), config.getServerPort());
                System.out.println(serverSocket);
            } catch (IOException e) {

                System.out.println("*** connection failed...attempt: " + (++attempts) + " ***");
                try {
                    Thread.sleep(SLEEP_TIME);
                } catch (InterruptedException e1) {

                }
            }
        } while (serverSocket == null && attempts < MAX_ATTEMPTS);

        // add (save) the socket so they can be use later
        if (attempts >= MAX_ATTEMPTS) {
            System.out.println("connection was unsuccessful, please try again later");
        } else {
            System.out.println("connected to master server with IP: " + ip + "and port: " + port);

            // map this peer to an output stream

            DataOutputStream serverOutputStream = new DataOutputStream(serverSocket.getOutputStream());

            this.server = new Server(ip, port, serverOutputStream, serverSocket);

            // tell the peer your host address and port number
            // tell the peer to connect to you
            CommonUtil.sendMessage(serverOutputStream, generateConnectJson());
        }

    }


    public boolean isUniqueConnection(String ip, int port) {
        return !isSelfConnection(ip, port) && isUniquePeer(ip, port);
    }


    private boolean isUniquePeer(String ip, int port) {
        return findPeer(ip, port) == null;
    }


    private boolean isSelfConnection(String ip, int port) {
        return ip.equals(myIP) && listenPort == port;
    }


    private void processSend(String userInput) {
        String[] args = userInput.split(" ");
        if (args.length >= 3) {
            try {
                int id = Integer.valueOf(args[1]) - 1;
                if (isValidPeer(id)) {
                    String msg = "";
                    for (int i = 2; i < args.length; i++)
                        msg += args[i] + " ";
                    CommonUtil.sendMessage(peerOutputMap.get(connectedClients.get(id)), generateMessageJson(msg));
                } else {
                    System.out.println("Error: Please select a valid peer id from the list command.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: Second argument should be a integer.");
            }
        } else {
            System.out.println("Error: Invalid format for 'send' command. See 'help' for details.");
        }
    }

    private void initRequest(String choice) {
        if (server != null) {
            try {
                String[] args = choice.split(" ");

                //add in request queue
                lamportClock.setClock(lamportClock.getClock() + 1);
                Request request = new Request(lamportClock);
                pq.add(new LamportClock(lamportClock.getClock(), lamportClock.getProcessId()));
                String id = getIdFromClock(request.getLamportClock());
                if (Type.getTypeFrom(args[0]) == Type.BALANCE) {
                    requests.put(id, new Message(Type.BALANCE));
                } else if (Type.getTypeFrom(args[0]) == Type.TRANSACTION)  {
                    requests.put(id, new Message(Type.TRANSACTION, Integer.parseInt(args[2]), args[1]));
                }
                replies.put(id, new ArrayList<>());

                //send REQUEST message to all peers, send process's lamport clock
                sendRequestMessageToPeers(request);

            } catch (NumberFormatException e) {
                System.out.println("Error: Second argument should be a integer.");
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            } catch (ArrayIndexOutOfBoundsException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Error: Invalid format for 'send' command. See 'help' for details.");
        }
    }

    private String getIdFromClock(LamportClock lamportClock) {
        return lamportClock.getClock() + "." + lamportClock.getProcessId();
    }

    private void sendRequestMessageToPeers(Request request) throws JsonProcessingException, InterruptedException {
        ObjectMapper objectMapper = new ObjectMapper();
        String message = JSONHelper.makeRequestJson(Type.REQUEST, myIP, listenPort, objectMapper.writeValueAsString(request)).toJSONString();
        for (Client client: connectedClients) {
            //
            System.out.println("Sent Request Message to Client: " + config.getProcessIds().get(client.getHost()));
            CommonUtil.sendMessage(peerOutputMap.get(client), message);
        }
    }

    private void sendReleaseMessageToPeers() throws JsonProcessingException, InterruptedException {
        String message = JSONHelper.makeJson(Type.RELEASE, myIP, listenPort).toJSONString();
        for (Client client: connectedClients) {
            System.out.println("Sent Release Message to Client: " + config.getProcessIds().get(client.getHost()));
            CommonUtil.sendMessage(peerOutputMap.get(client), message);
        }
    }


    private void processTerminate(String userInput) {
        String[] args = userInput.split(" ");
        if (args.length == 2) {
            try {
                int id = Integer.valueOf(args[1]) - 1;
                if (isValidPeer(id)) {
                    // notify peer that connection will be drop
                    Client peer = connectedClients.get(id);
                    CommonUtil.sendMessage(peerOutputMap.get(peer), generateTerminateJson());
                    System.out.println("You dropped peer [ip: " + peer.getHost() + " port: " + peer.getPort() + "]");
                    CommonUtil.terminateConnection(peer.getSocket(), peerOutputMap.get(peer));
                    removePeer(peer);
                } else {
                    System.out.println("Error: Please select a valid peer id from the list command.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Error: Second argument should be a integer.");
            }
        } else {
            System.out.println("Error: Invalid format for 'terminate' command. See 'help' for details.");
        }
    }


    private ServerSocket createListenSocket(String choice) throws IOException {

        if (isValidPortArg(choice)) {
            int port = Integer.valueOf(choice.split(" ")[1]);
            try {
                return listenSocket = new ServerSocket(port);
            } catch (Exception e) {
                return null;
            }
        } else {
            return null;
        }
    }


    private boolean isValidPortArg(String choice) {
        String[] args = choice.split(" ");

        // check if the argument length is 2
        if (args.length != 2) {
            System.out.println("invalid arguments: given: " + args.length + " expected: 2");
            return false;
        }

        // check if the port argument is valid
        if (!Validator.isValidPort(args[1])) {
            System.out.println("invalid port number");
            return false;
        }

        return true;
    }


    private void initPeer(String choice) throws IOException {
        listenSocket = createListenSocket(choice);

        if (listenSocket != null) {
            listenPort = listenSocket.getLocalPort();
            myIP = Inet4Address.getLocalHost().getHostAddress();
            System.out.println("you are listening on port: " + listenPort);
            startServer();
        }
    }
}
