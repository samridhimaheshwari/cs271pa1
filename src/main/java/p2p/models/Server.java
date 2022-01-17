package p2p.models;

import java.io.DataOutputStream;
import java.net.Socket;

public class Server {
    private String ip;
    private Integer port;
    private DataOutputStream dataOutputStream;
    private Socket socket;

    public Server(String ip, Integer port, DataOutputStream dataOutputStream, Socket socket) {
        this.ip = ip;
        this.port = port;
        this.dataOutputStream = dataOutputStream;
        this.socket = socket;
    }

    public String getIp() {
        return ip;
    }

    public Integer getPort() {
        return port;
    }

    public DataOutputStream getDataOutputStream() {
        return dataOutputStream;
    }

    public Socket getSocket() {
        return socket;
    }
}
