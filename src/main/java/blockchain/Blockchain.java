package blockchain;

import java.util.ArrayList;
import java.util.Random;

public class Blockchain {
    ArrayList<Block> blocks;

    Blockchain(){
        blocks  = new ArrayList<>();
    }

    public void addBlock(String sender, String receiver, int amount){
        System.out.println("Transaction for sender " + sender + " to "+ receiver + " for amount "+ amount);
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
}
