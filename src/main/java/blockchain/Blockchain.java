package blockchain;

import java.util.ArrayList;
import java.util.Random;

public class Blockchain {
    ArrayList<Block> blocks;
    private static final char[] hexArray = "0123456789abcdef".toCharArray();

    Blockchain(){
        blocks  = new ArrayList<>();
    }

    public void addBlock(String sender, String receiver, int amount){
        System.out.println("Processing transaction for sender " + sender + " to "+ receiver + " for amount "+ amount);
        System.out.println();
        byte[] previousHash;
        if(blocks.size()-1>=0) {
             previousHash = blocks.get(blocks.size() - 1).getPreviousHash();
        } else{
             previousHash = new byte[256];
            new Random().nextBytes(previousHash);
        }
        Block newBlock = new Block(sender, receiver, amount, previousHash);
        blocks.add(newBlock);
    }


    public int getBalance(String s){
        System.out.println("Checking balance for client " + s);
        System.out.println();
        int balance = 10;
        for(int i=0; i< blocks.size(); i++){
            int amount = blocks.get(i).getAmount();
            if(blocks.get(i).getSender().equals(s)){
                balance-=amount;
            }
            if(blocks.get(i).getReceiver().equals(s)){
                balance+= amount;
            }
        }
        return balance;

    }

    public void print(){
        for(int i=0; i<blocks.size(); i++){
            System.out.println("block " + i);
            System.out.println("sender "+ blocks.get(i).getSender());
            System.out.println("receiver "+ blocks.get(i).getReceiver());
            System.out.println("amount "+ blocks.get(i).getAmount()+"$");
            System.out.println("Hash "+ bytesToHex(blocks.get(i).getCurrentHash()));
            System.out.println("********************************************");
            System.out.println();

        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return String.valueOf(hexChars);
    }
}
