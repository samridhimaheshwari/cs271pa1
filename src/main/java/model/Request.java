package model;

import p2p.helpers.Type;

import java.io.Serializable;

public class Request implements Serializable {
        LamportClock lamportClock;


    public Request(LamportClock lamportClock) {
        this.lamportClock = lamportClock;

    }

    public LamportClock getLamportClock() {
        return lamportClock;
    }

}
