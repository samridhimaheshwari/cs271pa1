package util;

import java.io.Serializable;

public class LamportClock implements Serializable {
    int clock;
    int processId;

    public LamportClock(int processId) {
        this.clock = 0;
        this.processId = processId;
    }

    public int getClock() {
        return clock;
    }

    public void setClock(int clock) {
        this.clock = clock;
    }

    public int getProcessId() {
        return processId;
    }

    public void setProcessId(int processId) {
        this.processId = processId;
    }
}
