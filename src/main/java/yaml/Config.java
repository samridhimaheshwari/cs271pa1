package yaml;

import java.util.HashMap;
import java.util.List;

public class Config {
    private HashMap<String, Integer> processIds;
    private String serverIp;
    private Integer serverPort;
    private HashMap<String, String> peers;

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public HashMap<String, String> getPeers() {
        return peers;
    }

    public void setPeers(HashMap<String, String> peers) {
        this.peers = peers;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    public HashMap<String, Integer> getProcessIds() {
        return processIds;
    }

    public void setProcessId(HashMap<String, Integer> processIds) {
        this.processIds = processIds;
    }
}