package p2p.models;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {
	private String host;
	private int port;
	private Socket socket;

	public Client(String host, int port) {
		this.host = host;
		this.port = port;

		try {
			socket = new Socket(host, port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public Socket getSocket() {
		return socket;
	}

}
