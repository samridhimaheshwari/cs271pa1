package app;

import java.io.IOException;

import models.ChatApp;

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
