package p2p.app;

import java.io.IOException;

import p2p.models.ChatApp;

public class Chat {

    public static void main(String[] args) {

        try {
            ChatApp chat = new ChatApp();
            chat.acceptInputs();
        } catch (IOException e) {
           System.out.println(e);
        }

    }
}
