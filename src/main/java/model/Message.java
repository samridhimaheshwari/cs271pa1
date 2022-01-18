package model;

import p2p.helpers.Type;

public class Message {
    Type type;
    int amount;
    String receiver;

    public Message(Type type) {
        this.type = type;
    }

    public Message(Type type, int amount, String receiver) {
        this.type = type;
        this.amount = amount;
        this.receiver = receiver;
    }


    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }
}
