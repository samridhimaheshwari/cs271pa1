package p2p.app;

import java.io.IOException;

public class Application {

    public static void main(String[] args) {

        try {
            Peer chat = new Peer();
            chat.acceptInputs();
        } catch (IOException e) {
           System.out.println(e);
        }

    }
}
