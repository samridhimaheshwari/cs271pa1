package model;

import p2p.helpers.Type;

import java.io.Serializable;

public class Request implements Serializable {
        LamportClock lamportClock;
        Type message;


    public Request(LamportClock lamportClock, Type message) {
        this.lamportClock = lamportClock;
        this.message = message;
    }

    public void setMessage(String message) {

    }
    public LamportClock getLamportClock() {
        return lamportClock;
    }

    public Type getMessage() {
        return message;
    }
}
