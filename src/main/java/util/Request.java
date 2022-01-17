package util;

import java.io.Serializable;

public class Request implements Serializable {
        LamportClock lamportClock;
        String message;

    public Request(LamportClock lamportClock, String message) {
        this.lamportClock = lamportClock;
        this.message = message;
    }

    public LamportClock getLamportClock() {
        return lamportClock;
    }

    public String getMessage() {
        return message;
    }
}
