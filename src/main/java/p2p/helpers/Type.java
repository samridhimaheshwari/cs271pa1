package p2p.helpers;

public enum Type {
    CONNECT, MESSAGE, TERMINATE, BALANCE, TRANSACT, REPLY, REQUEST;

    private Type getType(String type) {
        for(Type t: Type.values()) {
            if (t.name().equals(type)) {
                return t;
            }
        }
        return null;
    }
}
