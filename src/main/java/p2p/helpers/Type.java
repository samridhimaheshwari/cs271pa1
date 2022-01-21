package p2p.helpers;

public enum Type {
    CONNECT("connect"), MESSAGE("message"), TERMINATE("terminate"), BALANCE("balance"), TRANSACTION("transaction"), REPLY("reply"), REQUEST("request"), RELEASE("release"), DROP("drop");

    String type;

    Type(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static Type getTypeFrom(String type) {
        for(Type t: Type.values()) {
            if (t.getType().equals(type)) {
                return t;
            }
        }
        return null;
    }
}
