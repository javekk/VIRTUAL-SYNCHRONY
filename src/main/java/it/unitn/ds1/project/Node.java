package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import scala.concurrent.duration.Duration;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.io.Serializable;


import java.lang.Thread;
import java.lang.InterruptedException;
import java.util.concurrent.TimeUnit;




/*
  *  @Author Raffaele Perini
  * @Author Giovanni Rafael Vuolo
  *
  */

class Node extends AbstractActor {



     //         ____                                        _
     //        / ___|   ___   _ __     ___   _ __    __ _  | |
     //       | |  _   / _ \ | '_ \   / _ \ | '__|  / _` | | |
     //       | |_| | |  __/ | | | | |  __/ | |    | (_| | | |
     //        \____|  \___| |_| |_|  \___| |_|     \__,_| |_|

    /*
     * The list of peers (the multicast group)
     */
    public List<ActorRef> group;

    /*
     * The list of peers names (the multicast group)
     */
    public List<String> view;

    /*
     * Number of current view
     */
    public int viewCounter;

    /*
     * Random Number used to random multicast
     */
    public Random rnd = new Random();


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
    public int id = 0;

    /*
     * Output to log
     */

    public String out = "";

    /*
     *   The local hashMap, in which we keep the last with the ActorRef and its own LastMessage
     *   (used as Buffer)
     */
    public HashMap<ActorRef,ChatMsg> lastMessages = new HashMap<>();


    /*
     * The chat history
     */
    public StringBuffer chatHistory = new StringBuffer();


    /*
     * Am I crashed?
     */
    public  boolean crashed = false;


    /*
     * Am I Unstable?
     */
    public  boolean unstable = false;


    /*
     * HashMap that track the FLush messages for each view
     */
    public List<ActorRef> flushMessagesTracker = new ArrayList<>();


    /*
     * Crashed Nodes with their Messages
     */
    public HashMap<ActorRef, ChatMsg> crashedNodesAndTheirMessages = new HashMap<>();

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
        // Empty mapping: we'll define it in the inherited classes
        return receiveBuilder().build();
    }


    /*
     * #1
     * Start message that informs every chat participant about its peers
     */
    public static class JoinGroupMsg implements Serializable {}

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
        public int sequenceNumber;

        public ChatMsg(int senderId, String text, int sequenceNumber) {
            this.senderId = senderId;
            this.text = text;
            this.sequenceNumber = sequenceNumber;
        }
    }

    /*
     * #4
     * A message requesting to print the chat history
     */
    public static class PrintHistoryMsg implements Serializable {}



    /*
     * #5
     * I have to ask to the Coordinator if I can join
     */
    public static class JoinRequest implements Serializable {}


    /*
     * #6
     * Message with the new view
     * Same class for both cohort and coordinator, but different in the action implementation
     * fot this reason this Class is map in the subclasses
     */
    public static class NewView implements Serializable{
        public final List<ActorRef> group;    // an array of group members
        public final List<String> view;
        public final int viewCounter;
        public NewView(List<ActorRef> group, List<String> view, int viewCounter) {
            // Copying the group as an unmodifiable list
            this.group = new ArrayList<ActorRef>(group);
            this.view = new ArrayList<String>(view);
            this.viewCounter = viewCounter;
        }
    }


    /*
     * #7
     * I can join the team, yea
     */
    public static class CanJoin implements Serializable{
        public final NewView newView;    // an array of group members
        public CanJoin(NewView newView) {
            // Copying the group as an unmodifiable list
            this.newView = newView;
        }
    }



    /*
     * #8
     * new Id for the new Node joined
     */
    public static class NewId implements Serializable{
        public final int newId;
        public NewId (int newId ) {
            this.newId = newId;
        }
    }


    /*
     * #9
     * Unstable Message
     */
    public static class Unstable implements Serializable{
        public final NewView view;
        public Unstable(NewView view ){
            this.view = view;
        }
    }


    /*
     * #10
     * Stable Message
     */
    public static class Flush implements Serializable{
        public final NewView view;
        public final HashMap<ActorRef, ChatMsg> crashedNodesWithLastMessages;
        public Flush(NewView view , HashMap<ActorRef, ChatMsg> crashedNodesWithLastMessages){
            this.view = view;
            this.crashedNodesWithLastMessages = crashedNodesWithLastMessages;
        }
    }


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
    public void onJoinGroupMsg(JoinGroupMsg msg) {
        System.out.println("\u001B[36m " + getSelf().path().name() +": joining a group of " + this.group.size() + "peers with ID " + this.id);
    }


    /*
     * #2
     * The first Time I want to send a message a pass through this method then,
     * look at sendChatMsg Method
     */
    public void onStartChatMsg(StartChatMsg msg) {

        getContext().system().scheduler().schedule(
                Duration.create((int)(5000*Math.random()), TimeUnit.MILLISECONDS),
                Duration.create((int)(5000*Math.random())+1000, TimeUnit.MILLISECONDS),
                () -> sendChatMsg(),
                getContext().system().dispatcher());

    }


    /*
     * #4
     * Print the History of this node
     */
    public void printHistory(PrintHistoryMsg msg) {
        System.out.printf("%02d: %s\n", this.id, this.chatHistory);
    }


    /*
     * #9
     * Unstable Message
     */
    public void onUnstable(Unstable unstable){
        this.unstable = true;
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
    public void sendChatMsg() {

        if (crashed || unstable) return;


        if(this.id == 3){
            System.err.print( "Message in multicast -> id: " + this.id + ", text: LOST_MESSAGE" +"\n");
            this.group.get(1).tell(new ChatMsg(this.id, "LOST_MESSAGE", ++this.sendCount), getSelf());
            getSelf().tell(new NodePartecipant.Crash(60), null);
            try {
                Thread.sleep(1000);
                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        this.sendCount++;
        ChatMsg m = new ChatMsg(this.id,"[~" + numberToString((int) (Math.random()*1000000)%99999) + "]", this.sendCount);
        out = this.id + " send multicast " + m.text + " within " + this.viewCounter + "\n";
        MulticastLog(out);
        System.err.print( "Message in multicast -> id: " + this.id + ", text: " + m.text +"\n");
        multicast(m);
        appendToHistory(m); // append the sent message
    }


    /*
     * We model random network delays with this code
     */
    public void multicast(Serializable m) { // our multicast implementation
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
    public void deliver(ChatMsg m) {

        appendToHistory(m);
        out = this.id + " deliver multicast " + m.text + " from "  + m.senderId + " within " + this.viewCounter + "\n";
        MulticastLog(out);
        System.out.println("\u001B[35m" + "Message \"" + m.text + "\" from " + m.senderId + " deliver to Node: " + this.id);

    }


    /*
     * Append to the History
     */
    public void appendToHistory(ChatMsg m) {
        this.chatHistory.append(  "[" + m.senderId + "," + m.text + "]");
    }


    /*
     * Transform a Number to A String
     */
    public String numberToString(int number){
        String ret = "";
        String digits = Integer.toString(number);
        for(int i = 0; i < digits.length(); i++){
            try{ret = ret.concat(getCharForNumber(Integer.parseInt(digits.charAt(i)+"")));}
            catch (Exception e) {ret = ret.concat(Integer.toString((int)Math.random()*10 % 4));}
        }
        return ret;
    }
    public String getCharForNumber(int i) {
        return i > 0 && i < 27 ? String.valueOf((char)(i + 64)) : null;
    }

    /*
     * Creates log file
     */
    public void MulticastLog(String text) {

        String FILENAME = "multicast.log";

        BufferedWriter bw = null;
        FileWriter fw = null;

        try {

            // String content = "This is the content to write into file\n";

            fw = new FileWriter(FILENAME, true);
            bw = new BufferedWriter(fw);
            bw.write(text);

            //System.out.println("Done");

        } catch (IOException e) {

            e.printStackTrace();

        } finally {

            try {

                if (bw != null)
                    bw.close();

                if (fw != null)
                    fw.close();

            } catch (IOException ex) {

                ex.printStackTrace();

            }

        }

    }


    /*
     * Check if the last message arrived from a crashed nodes is really the last message
     */
    public void checkForLastMessages(HashMap<ActorRef, ChatMsg> hm){


        for(Map.Entry<ActorRef, ChatMsg> entry : hm.entrySet()){
            ActorRef key = entry.getKey();
            ChatMsg value = entry.getValue();

            if(this.lastMessages.get(key) == null && value != null){
                deliver(value);
                lastMessages.put(key, value);
            }
            else if(value != null && value.sequenceNumber == this.lastMessages.get(key).sequenceNumber+1 ){
                deliver(lastMessages.get(key));
                deliver(value);
                lastMessages.put(key, value);
            }
        }

    }



    /*
     * Map each crashed node with the last message arrived in this node
     */
    public HashMap<ActorRef, ChatMsg> checkForCrashedNodesMessages(NewView newView) {

        HashMap<ActorRef, ChatMsg> ret = new HashMap<>();

        if (this.group != null && this.group.size() > newView.group.size()) {

            List<ActorRef> CrashedPeers = new ArrayList<>(this.group);
            CrashedPeers.removeIf(item -> newView.group.contains(item));
            String s = getSelf().path().name() + "->";
            for (ActorRef a : CrashedPeers) {
                if(this.lastMessages.get(a)!=null){
                    ret.put(a, this.lastMessages.get(a));
                    s = s.concat("[" + a.path().name() + ";" + this.lastMessages.get(a).text + "]");
                }
                else {
                    ret.put(a, null);
                    s = s.concat("[ " + a.path().name() + ";" + " no message yet]");
                }

            }
            System.out.println(s);
        }
        return ret;

    }

}
