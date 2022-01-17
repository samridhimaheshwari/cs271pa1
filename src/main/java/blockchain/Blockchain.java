package blockchain;

import java.util.ArrayList;

public class Blockchain {
    ArrayList<Block> blocks;


    public void addBlock(String sender, String receiver, int amount){
        byte[] previousHash = blocks.get(blocks.size()-1).getPreviousHash();
        Block newBlock = new Block(sender, receiver, amount, previousHash);
        blocks.add(newBlock);
    }


    public int getBalance(String s){
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
