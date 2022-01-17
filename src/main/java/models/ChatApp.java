package models;

import java.io.*;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.Map.Entry;

import helpers.JSONHelper;
import helpers.Type;
import helpers.Validator;
import org.yaml.snakeyaml.Yaml;
import util.LamportClock;
import util.Request;
import yaml.Config;

public class ChatApp {

	private List<Peer> connectedPeers;
	private Integer listenPort;
	private String myIP;
	private ServerSocket listenSocket;
	private final int MAX_CONNECTIONS = 3;
	private BufferedReader input;
	private Map<Peer, DataOutputStream> peerOutputMap;
	private PriorityQueue<Request> requestQueue;
	private LamportClock lamportClock;
	int processId;
	private Yaml yaml;


	public ChatApp() throws IOException {

		//get IP Address of Self, processId, and IP addresses of other clients from configuration file

		yaml = new Yaml();
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("/yaml/config.yaml");
		Config config = yaml.load(inputStream);
		myIP = "169.231.195.21";
	    
		// list of all clients (peers) connected to this host
		connectedPeers = new ArrayList<Peer>();

		input = new BufferedReader(new InputStreamReader(System.in));

		// map a peer to an output stream
		peerOutputMap = new HashMap<Peer, DataOutputStream>();

		lamportClock = new LamportClock(config.getProcessId());
	}

	// for testing purposes
	public ChatApp(int port) throws IOException {
		this();
		listenPort = port;
		listenSocket = new ServerSocket(listenPort);
	}

	/**
	 * start the server, listen to connection, and respond to user's command
	 * line input.
	 */
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
	private class PeerHandler implements Runnable {

		private Socket peerSocket;

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
					int port = Integer.valueOf(JSONHelper.parse(jsonStr, "port"));

					// each JSON string received/written can be of 3 types
					Type type = Type.valueOf(JSONHelper.parse(jsonStr, "type"));
					switch (type) {
					case CONNECT:
						displayConnectSuccess(jsonStr);
						break;
					case MESSAGE:
						String message = JSONHelper.parse(jsonStr, "message");
						displayMessage(ip, port, message);
						break;
					case TERMINATE:
						displayTerminateMessage(ip, port);
						terminateConnection(findPeer(ip, port));
						removePeer(findPeer(ip, port));
						input.close();
						return;
					}
				}
			} catch (IOException e) {
				System.out.println("Message: Connection drop");
			}
		}
	}

	// display a list of user commands, the correct way to enter it, and a
	// description
	private void displayManual() {
		for (int i = 0; i < 100; i++)
			System.out.print("-"); // header
		System.out.println("\nchat <port number>\t Run chat listening on <port number>");
		System.out.println("\nhelp\tDisplay information about the available user interface commands.");
		System.out.println("\nmyip\t Display your IP address.");
		System.out
				.println("\nmyport\t Display the port # on which this process is listening for incoming connections.");
		System.out.println("\nconnect\t <destination> <port no> This command establishes a new TCP connection to the "
				+ "specified <destination> at the \nspecified <port no>. <destination> is the IP address of the destination.");
		System.out.println("\nlist Display a list of all the peers you are connected to. More specifically, it displays"
				+ "the index id #, IP address, and port # of each peer.");
		System.out.println(
				"\nterminate <connection id> Terminate the connection to a peer by their id given in the list command.");
		System.out.println(
				"\nsend\t <connection id.> <message> Send a message to a peer by their id given in the list command."
						+ "The message to be sent can be \nup-to 100 characters long, including blank spaces.");
		System.out.println("\nexit\t Close all connections and terminate this process.");
		for (int i = 0; i < 100; i++)
			System.out.print("-"); // footer
		System.out.println("\n");
	}

	// accept commands from the users
	public void acceptInputs() throws IOException {
		System.out.println("Welcome to Chat 470");

		while (true) {
			System.out.print("-> ");
			String choice = input.readLine();
			// the first argument is the command
			String option = choice.split(" ")[0].toLowerCase();

			switch (option) {
			case "chat":
				if (listenSocket == null)
					initChat(choice);
				else
					System.out.println("Error: you can only listen to one port at a time");
				break;
			case "help":
				displayManual();
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
			case "connect":
				if (listenSocket == null)
					System.out.println("Error: you are not connected");
				else
					processConnect(choice);
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
		if (connectedPeers.isEmpty())
			System.out.println("No peers connected.");
		else {
			System.out.println("id:   IP Address     Port No.");
			for (int i = 0; i < connectedPeers.size(); i++) {
				Peer peer = connectedPeers.get(i);
				System.out.println((i + 1) + "    " + peer.getHost() + "     " + peer.getPort());
			}
			System.out.println("Total Peers: " + connectedPeers.size());
		}
	}

	// find the client on host's list (connectedPeers)
	// and write to them a message
	private void sendMessage(Peer peer, String jsonString) {
		try {
			// "\r\n" so when readLine() is called,
			// it knows when to stop reading
			peerOutputMap.get(peer).writeBytes(jsonString + "\r\n");

		} catch (Exception e) {
			System.out.println(e);
		}
	}

	// create a socket, connect to peer
	private void connect(String ip, int port) throws IOException {

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
			Peer peer = new Peer(ip, port);
			connectedPeers.add(peer);

			// map this peer to an output stream
			peerOutputMap.put(peer, new DataOutputStream(peerSocket.getOutputStream()));

			// tell the peer your host address and port number
			// tell the peer to connect to you
			sendMessage(peer, generateConnectJson());
		}
	}

	/**
	 * tells each peer to close their connection with this process; process
	 * closes all of its own connections.
	 * 
	 * @throws IOException
	 */
	private void breakPeerConnections() throws IOException {

		// terminate each peer connection; notify them
		for (Peer peer : connectedPeers) {
			sendMessage(peer, generateTerminateJson());
			terminateConnection(peer);
		}

		// close each output stream
		for (Entry<Peer, DataOutputStream> e : peerOutputMap.entrySet()) {
			e.getValue().close();
		}

		listenSocket.close();
		System.out.println("chat client close, good bye");
	}

	/**
	 * remove the peer from all data structure
	 * 
	 * @param peer
	 */
	private void removePeer(Peer peer) {
		connectedPeers.remove(peer);
		peerOutputMap.remove(peer);
	}

	/**
	 * @return a JSON String that indicate to another peer (client) to connect
	 *         to the host socket. For more information, see
	 *         JSONHelper.makeJson()
	 */
	private String generateConnectJson() {
		return JSONHelper.makeJson(Type.CONNECT, myIP, listenPort).toJSONString();
	}

	/**
	 * @return a JSON String that indicate to another peer (client)that they
	 *         have received a message. For more information, see
	 *         JSONHelper.makeJson()
	 */
	private String generateMessageJson(String message) {
		return JSONHelper.makeJson(Type.MESSAGE, myIP, listenPort, message).toJSONString();
	}

	/**
	 * @return a JSON String that indicate to another peer (client) to terminate
	 *         the host socket. For more information, see JSONHelper.makeJson()
	 */
	private String generateTerminateJson() {
		return JSONHelper.makeJson(Type.TERMINATE, myIP, listenPort).toJSONString();
	}

	// id = index, thus 0 to size() - 1
	private boolean isValidPeer(int id) {
		return id >= 0 && id < connectedPeers.size();
	}

	/**
	 * 
	 * @param ip
	 *            the peer's ip address
	 * @param port
	 *            the peer's port number
	 * @return the peer (from connectedPeers) with the corresponding ip and port
	 *         number. if no peer exist with the corresponding information, will
	 *         return null instead
	 */
	private Peer findPeer(String ip, int port) {
		for (Peer p : connectedPeers)
			if (p.getHost().equals(ip) && p.getPort() == port)
				return p;
		return null;
	}

	/**
	 * close the peer socket and output stream
	 * 
	 * @param peer
	 */
	private void terminateConnection(Peer peer) {
		try {
			peer.getSocket().close();
			peerOutputMap.get(peer).close();
		} catch (IOException e) {
			
		}
	}

	/**
	 * display message received from peer along with their port and IP address
	 * 
	 * @param ip
	 * @param port
	 * @param message
	 */
	private void displayMessage(String ip, int port, String message) {
		System.out.println("\nMessage received from IP: " + ip);
		System.out.println("Sender's Port: " + port);
		System.out.println("Message: " + message);

		// "->" doesn't display after the user receive a
		// message
		System.out.print("-> ");
	}

	/**
	 * display a notification that a client has connected to the host. create a
	 * peer object, with peer's connection info, and add them to connectedPeers.
	 * also add the peer to a map with an output stream object.
	 * 
	 * @param jsonStr
	 *            Stringify JSON object
	 * @throws IOException
	 */
	private void displayConnectSuccess(String jsonStr) throws IOException {
		String ip = JSONHelper.parse(jsonStr, "ip");
		int port = Integer.valueOf(JSONHelper.parse(jsonStr, "port"));
		System.out.println("\nPeer [ip: " + ip + ", port: " + port + "] connects to you");
		System.out.print("-> ");
		// save peer's info, used for a lot of other stuff
		Peer peer = new Peer(ip, port);
		connectedPeers.add(peer);
		peerOutputMap.put(peer, new DataOutputStream(peer.getSocket().getOutputStream()));
	}

	/**
	 * notify the user that a peer has drop the connection.
	 * 
	 * @param jsonStr
	 */
	private void displayTerminateMessage(String ip, int port) {
		System.out.println();
		System.out.println("Peer [ip: " + ip + " port: " + port + "] has terminated the connection");
		System.out.print("-> ");
	}

	/**
	 * take the user's input and if it is valid (contains keyword "connect", ip,
	 * and a valid port) AND if the host has not exceeded MAX_CONNECTION,
	 * attempt to connect to the socket with the provided information
	 * 
	 * @param userInput
	 *            user command line input
	 * @throws IOException
	 */
	private void processConnect(String userInput) throws IOException {
		String[] args = userInput.split(" ");
		String ip;
		int port;

		// check if the user input is "valid"
		if (!Validator.isValidConnect(userInput)) {
			System.out.println("connect fail: invalid arguments");
			return;
		}

		ip = args[1];
		port = Integer.valueOf(args[2]);

		// check if connection limited is exceeded
		if (connectedPeers.size() >= MAX_CONNECTIONS) {
			System.out.println("connect fail: max connection");
			return;
		}

		// check for self/duplicate connections
		if (!isUniqueConnection(ip, port)) {
			System.out.println("connect fail: no self or duplicate connection");
			return;
		}

		// all tests passed, connect to the peer
		connect(ip, port);
	}

	/**
	 * 
	 * @param ip
	 * @param port
	 * @return true if the ip and port and not identical to the host AND not the
	 *         same as another peers to which the host is connected to. in other
	 *         words, all connection must be unique.
	 */
	public boolean isUniqueConnection(String ip, int port) {
		return !isSelfConnection(ip, port) && isUniquePeer(ip, port);
	}

	/**
	 * 
	 * @param ip
	 * @param port
	 * @return true if the ip and port is unique, not an "active" connection
	 */
	private boolean isUniquePeer(String ip, int port) {
		return findPeer(ip, port) == null;
	}

	/**
	 * 
	 * @param ip
	 * @param port
	 * @return true if the connection information is the host active open
	 *         connection
	 */
	private boolean isSelfConnection(String ip, int port) {
		return ip.equals(myIP) && listenPort == port;
	}

	// mostly used for testing purpose
	public void setConnectedPeers(List<Peer> peers) {
		if (peers.size() <= MAX_CONNECTIONS)
			connectedPeers = peers;
	}

	/**
	 * Validate user input during the 'send' function. User input must be at
	 * least 3 arguments separated by spaces. The second argument has to be a
	 * integer and a valid peer id on the list. The third argument is the
	 * message to send.
	 * 
	 * @param userInput
	 *            user command line input
	 * 
	 */
	private void processSend(String userInput) {
		String[] args = userInput.split(" ");
		if (args.length >= 3) {
			try {
				int id = Integer.valueOf(args[1]) - 1;
				if (isValidPeer(id)) {
					String msg = "";
					for (int i = 2; i < args.length; i++)
						msg += args[i] + " ";
					sendMessage(connectedPeers.get(id), generateMessageJson(msg));
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

	/**
	 * Validate user input during the terminate command. User input must exactly
	 * 2 arguments separated by spaces. The second argument must be a integer
	 * and a valid peer id on the list.
	 * 
	 * @param userInput
	 *            user command line input
	 */
	private void processTerminate(String userInput) {
		String[] args = userInput.split(" ");
		if (args.length == 2) {
			try {
				int id = Integer.valueOf(args[1]) - 1;
				if (isValidPeer(id)) {
					// notify peer that connection will be drop
					Peer peer = connectedPeers.get(id);
					sendMessage(peer, generateTerminateJson());
					System.out.println("You dropped peer [ip: " + peer.getHost() + " port: " + peer.getPort() + "]");
					terminateConnection(peer);
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

	/**
	 * Try to create a listening socket based on user input.
	 * 
	 * @param choice
	 *            user's command line input
	 * @throws IOException
	 */
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

	/**
	 * 
	 * @param choice
	 *            user command line input
	 * @return true if the user enter the correct amount of arguments (2) and
	 *         the port number (second argument) is "valid"
	 */
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

	/**
	 * assign listen port and initialize/start the server socket.
	 * 
	 * @param choice
	 *            user command line input
	 * @throws IOException
	 */
	private void initChat(String choice) throws IOException {
		listenSocket = createListenSocket(choice);

		if (listenSocket != null) {
			listenPort = listenSocket.getLocalPort();
			myIP = "169.231.195.21";
			System.out.println("you are listening on port: " + listenPort);
			startServer();
		}
	}
}
