package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.AbstractActor;

import java.util.*;
import java.io.Serializable;
import akka.actor.Props;
import scala.Int;

import java.lang.Thread;
import java.lang.InterruptedException;



/*
  *  @Author Raffaele Perini
  * @Author Giovanni Rafael Vuolo
  *
  */

class Chatter extends AbstractActor {



     //         ____                                        _
     //        / ___|   ___   _ __     ___   _ __    __ _  | |
     //       | |  _   / _ \ | '_ \   / _ \ | '__|  / _` | | |
     //       | |_| | |  __/ | | | | |  __/ | |    | (_| | | |
     //        \____|  \___| |_| |_|  \___| |_|     \__,_| |_|


    /*
     * Limit of the messages for the node
     */
    private final static int N_MESSAGES = 3;

    /*
     * The list of peers (the multicast group)
     */
    private List<ActorRef> group;

    /*
     * Random Number used to random multicast
     */
    private Random rnd = new Random();



    //         _   _               _            ____
    //        | \ | |   ___     __| |   ___    |  _ \   _ __    ___    _ __    ___
    //        |  \| |  / _ \   / _` |  / _ \   | |_) | | '__|  / _ \  | '_ \  / __|
    //        | |\  | | (_) | | (_| | |  __/   |  __/  | |    | (_) | | |_) | \__ \
    //        |_| \_|  \___/   \__,_|  \___|   |_|     |_|     \___/  | .__/  |___/
    //                                                                |_|

    /*
     *   number of sent messages
     */
    private int sendCount = 0;

    /*
     *    ID of the current actor
     */
    private final int id;

    /*
     *   The local hashMap, in which we keep the last with the ActorRef and its own LastMessage
     *   (used as Buffer)
     */
    private HashMap<ActorRef,ChatMsg> lastMessages = new HashMap<>();


    /*
     * The chat history
     */
    private StringBuffer chatHistory = new StringBuffer();


    /*
     * Actor Constructor
     */
    private Chatter(int id) {
        this.id = id;
    }

    static Props props(int id) {
        return Props.create(Chatter.class, () -> new Chatter(id));
    }


    //         __  __                   _____                                             ____   _
    //        |  \/  |  ___    __ _    |_   _|  _   _   _ __     ___   ___               / ___| | |   __ _   ___   ___    ___   ___
    //        | |\/| | / __|  / _` |     | |   | | | | | '_ \   / _ \ / __|    _____    | |     | |  / _` | / __| / __|  / _ \ / __|
    //        | |  | | \__ \ | (_| |     | |   | |_| | | |_) | |  __/ \__ \   |_____|   | |___  | | | (_| | \__ \ \__ \ |  __/ \__ \
    //        |_|  |_| |___/  \__, |     |_|    \__, | | .__/   \___| |___/              \____| |_|  \__,_| |___/ |___/  \___| |___/
    //                        |___/             |___/  |_|


    /*
     * Here we define the mapping between the received message types
     * and our actor methods
     *
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(JoinGroupMsg.class,    this::onJoinGroupMsg)    //#1
                .match(StartChatMsg.class,    this::onStartChatMsg)    //#2
                .match(ChatMsg.class,         this::onChatMsg)         //#3
                .match(PrintHistoryMsg.class, this::printHistory)      //#4
                .build();
    }



    /*
     * #1
     * Start message that informs every chat participant about its peers
     */
    public static class JoinGroupMsg implements Serializable {
        private final List<ActorRef> group; // list of group members
        public JoinGroupMsg(List<ActorRef> group) {
            this.group = Collections.unmodifiableList(group);
        }

    }

    /*
     * #2
     * A message requesting the peer to start to discus
     */
    public static class StartChatMsg implements Serializable {}


    /*
     * #3
     * Chat message, a normal message
     */
    public static class ChatMsg implements Serializable {

        public final int senderId;   // the ID of the message sender
        public String text;         // text of the messagges

        public ChatMsg(int senderId, String text) {
            this.senderId = senderId;
            this.text = text;
        }
    }

    /*
     * #4
     * A message requesting to print the chat history
     */
    public static class PrintHistoryMsg implements Serializable {}




     //             _             _                                ____           _
     //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
     //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
     //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
     //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/


    /*
     * #1
     * When a node enters in the group, it first of all get all the peers(init the group variable)
     * Then print the the fact that it joined
     */
    private void onJoinGroupMsg(JoinGroupMsg msg) {

        this.group = msg.group;

        System.out.printf("%s: joining a group of %d peers with ID %02d\n",
                getSelf().path().name(), this.group.size(), this.id);
    }


    /*
     * #2
     * The first Time I want to send a message a pass through this method then,
     * look at sendChatMsg Method
     */
    private void onStartChatMsg(StartChatMsg msg) {
        sendChatMsg(); // start with message 0
    }


    /*
     * #3
     * When we received a Message
     * I get the last Message from the sender(to drop)
     * I replace the last message with the new one
     * I deliver/drop the Old Message
     */
    private void onChatMsg(ChatMsg msg) {

        ChatMsg drop;
        if(lastMessages.get(group.get(msg.senderId)) != null){
            drop = lastMessages.get(group.get(msg.senderId));
            System.out.println("\u001B[32m" + "Message \"" + drop.text + "\" " + "from Node: " + msg.senderId + " dropped by Node: " + this.id);

        }
        lastMessages.put(group.get(msg.senderId), msg);
        deliver(msg);
    }


    /*
     * #4
     * Print the History of this node
     */
    private void printHistory(PrintHistoryMsg msg) {
        System.out.printf("%02d: %s\n", this.id, this.chatHistory);
    }



    //         _   _          _           _                     _____
    //        | | | |   ___  | |  _ __   (_)  _ __     __ _    |  ___|  _   _   _ __     ___   ___
    //        | |_| |  / _ \ | | | '_ \  | | | '_ \   / _` |   | |_    | | | | | '_ \   / __| / __|
    //        |  _  | |  __/ | | | |_) | | | | | | | | (_| |   |  _|   | |_| | | | | | | (__  \__ \
    //        |_| |_|  \___| |_| | .__/  |_| |_| |_|  \__, |   |_|      \__,_| |_| |_|  \___| |___/
    //                           |_|                  |___/


    /*
     * First I update my sendCount
     * Init of the ChatMsg class
     * Print of the msg and the id
     * Multicast of the message
     * appentToHistory
     */
    private void sendChatMsg() {
        this.sendCount++;
        ChatMsg m = new ChatMsg(this.id,"[~" + numberToString((int) (Math.random()*1000000)%99999) + "]");
        System.err.print( "Message in multicast -> id:" + this.id + ", text: " + m.text +"\n");
        multicast(m);
        appendToHistory(m); // append the sent message
    }


    /*
     * We model random network delays with this code
     */
    private void multicast(Serializable m) { // our multicast implementation
        List<ActorRef> shuffledGroup = new ArrayList<>(group);
        Collections.shuffle(shuffledGroup);
        for (ActorRef p: shuffledGroup) {
            if (!p.equals(getSelf())) { // not sending to self
                p.tell(m, getSelf());
                try { Thread.sleep(rnd.nextInt(10)); }
                catch (InterruptedException e) { e.printStackTrace(); }
            }
        }
    }



    /*
     *  When a Message is stable, Hence I have received another message from the same sender (ò.ò) OOOOMMMMGGGGG
     *  I happend it to history and I deliver/drop it
     *  then If I have other msg to send I do it
     */
    private void deliver(ChatMsg m) {

        appendToHistory(m);
        System.out.println("\u001B[35m" + "Message \"" + m.text + "\" from " + m.senderId + " deliver to Node: " + this.id);

        if(sendCount < N_MESSAGES){
            sendChatMsg();
        }

    }


    /*
     * Append to the History
     */
    private void appendToHistory(ChatMsg m) {
        this.chatHistory.append(  "[" + m.senderId + "," + m.text + "]");
    }


    /*
     * Transform a Number to A String
     */
    private String numberToString(int number){
        String ret = "";
        String digits = Integer.toString(number);
        for(int i = 0; i < digits.length(); i++){
            try{ret = ret.concat(getCharForNumber(Integer.parseInt(digits.charAt(i)+"")));}
            catch (Exception e) {ret = ret.concat(Integer.toString((int)Math.random()*10 % 4));}
        }
        return ret;
    }
    private String getCharForNumber(int i) {
        return i > 0 && i < 27 ? String.valueOf((char)(i + 64)) : null;
    }
}