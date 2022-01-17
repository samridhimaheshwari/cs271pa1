package yaml;

import java.util.List;

public class Config {
    private Integer processId;

    private List<String> clients;

    public Integer getProcessId() {
        return processId;
    }

    public List<String> getClients() {
        return clients;
    }
}