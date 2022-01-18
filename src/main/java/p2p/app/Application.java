package p2p.app;

import java.io.IOException;

public class Application {

    public static void main(String[] args) {

        try {
            PeerService peerService = new PeerService();
            peerService.acceptInputs();
        } catch (IOException | InterruptedException e) {
           System.out.println(e);
        }

    }
}
