package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import scala.concurrent.duration.Duration;

import java.util.*;
import java.io.Serializable;

import java.lang.Thread;
import java.lang.InterruptedException;
import java.util.concurrent.TimeUnit;

 /*
  *@Author Raffaele Perini
  *@Author Giovanni Rafael Vuolo
  *
  */
class Node extends AbstractActor {

    //         _   _               _            ____
    //        | \ | |   ___     __| |   ___    |  _ \   _ __    ___    _ __    ___
    //        |  \| |  / _ \   / _` |  / _ \   | |_) | | '__|  / _ \  | '_ \  / __|
    //        | |\  | | (_) | | (_| | |  __/   |  __/  | |    | (_) | | |_) | \__ \
    //        |_| \_|  \___/   \__,_|  \___|   |_|     |_|     \___/  | .__/  |___/
    //                                                                |_|


     /*
      * Current View
      */
    public View view;


     /*
      * Random Number used to random multicast
      */
    public Random rnd = new Random();



     /*
     *   number of sent messages
     */
    private int sendCount = 0;


    /*
     *    ID of the current actor
     */
    public int id = 0;


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
    public  int inhibit_sends = 0;


    /*
     * Buffer in which we put the messages to deliver in views not yet installed
     */
    public List<ChatMsg> messagesBuffer = new ArrayList<>();


    /*
     * List of received flush messages
     */
    public List<Flush> flushBuffer = new ArrayList<>();


    /*
     * Contructor
     */


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
        public final String text;          // text of the messagges
        public final int viewNumber;       // view in which the message belongs
        public final int sequenceNumber;   // sequence number of the message
        public final boolean isACopy;      // to say if we're dealing with unstable messages

        public ChatMsg(int senderId, String text, int viewNumber, int sequenceNumber, boolean isACopy) {
            this.senderId = senderId;
            this.text = text;
            this.viewNumber = viewNumber;
            this.sequenceNumber = sequenceNumber;
            this.isACopy = isACopy;
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
    public static class View implements Serializable{
        public final List<ActorRef> group;
        public final List<String> viewAsString;
        public final int viewCounter;
        public View(List<ActorRef> group, List<String> viewAsString, int viewCounter) {
            this.group = group;
            this.viewAsString = viewAsString;
            this.viewCounter = viewCounter;
        }
    }


    /*
     * #8
     * new Id for the new Node joined
     */
    public static class initNode implements Serializable{
        public final int newId;
        public final View initialView;
        public initNode(int newId, View initialView ) {
            this.newId = newId;
            this.initialView = initialView;
        }
    }


    /*
     * Flush Message
     */
    public static class Flush implements Serializable{
        public final View view;
        public Flush(View view){
            this.view = view;
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
        System.out.println("\u001B[36m " + getSelf().path().name() +": joining a group of " + this.view.group.size() + "peers with ID " + this.id);
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
      * onFLusho
      */
     public void onFlush(Flush flush){

        this.flushBuffer.add(flush);

        int numberOfFlushNeeded = Integer.MAX_VALUE;

        List<Flush> currentFlushesForTheNextView = new ArrayList<>();

        System.out.println("\u001B[33m" + getSelf().path().name() + " FLUSH arrived from " + getSender().path().name() + " for the view " + flush.view.viewCounter); //add list of node inside view


        for(Flush f: this.flushBuffer){
            numberOfFlushNeeded = Math.min(numberOfFlushNeeded, f.view.group.size());
            if(f.view.viewCounter == this.view.viewCounter+1){
                currentFlushesForTheNextView.add(f);
            }
        }

        if(currentFlushesForTheNextView.size() == numberOfFlushNeeded-1){
             this.view = currentFlushesForTheNextView.get(0).view; //install the new view i+1
             this.flushBuffer.removeAll(currentFlushesForTheNextView);
             System.out.println("\u001B[33m" + getSelf().path().name() + " INSTALL a new View: " + this.view.viewAsString.toString()); //add list of node inside view
             this.inhibit_sends--;
         }
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

        if (crashed || (inhibit_sends > 0) || view == null) return;


        this.sendCount++;
        /*
        if(this.id == 3){
            System.err.print( "Message in multicast -> id: " + this.id + ", text: LOST_MESSAGE" +"\n");
            this.view.getGroup().get(1).tell(new ChatMsg(this.id, "LOST_MESSAGE", this.view.getViewCounter(), this.sendCount, false), getSelf());
            getSelf().tell(new NodeParticipant.Crash(60), null);
            try {
                Thread.sleep(1000);
                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
        */
        ChatMsg m = new ChatMsg(
                this.id,
                "[~" + numberToString((int) (Math.random()*1000000)%99999) + "]",
                this.view.viewCounter,
                this.sendCount,
                false);

        System.out.println(" \u001B[31m Message in multicast -> id: " + this.id + ", text: " + m.text + " in the view: " + this.view.viewCounter);
        multicast(m, this.view);
        appendToHistory(m); // append the sent message
    }


    /*
     * We model random network delays with this code
     */
    public void multicast(Serializable m, View view) { // our multicast implementation
        List<ActorRef> shuffledGroup = new ArrayList<>(view.group);
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
     * Deliver the message
     */
    public void deliver(ChatMsg m) {
        if(m != null) {
            appendToHistory(m);
            System.out.println("\u001B[35m" + "Message \"" + m.text + "\" from " + getSender().path().name() + " deliver to " + this.id + " within the view " + m.viewNumber);
        }
    }


    /*
     * Append to the History
     */
    public void appendToHistory(ChatMsg m) {
            this.chatHistory.append("[" + m.senderId + "," + m.text + "]");
    }


     /*
      *
      */
     public void multicastAllUnstableMessages(View view){

         for (Map.Entry<ActorRef, ChatMsg> entry : lastMessages.entrySet()){

             ChatMsg unstableMsg = entry.getValue();

             if(unstableMsg.viewNumber < view.viewCounter){

                 multicast(new ChatMsg( unstableMsg.senderId,
                         unstableMsg.text,
                         view.viewCounter, // we send the unstable message in the new view
                         unstableMsg.sequenceNumber,
                         true), //is a copy?
                         view);

                 /*Now the message is stable, so I can deliver it*/
                 deliver(unstableMsg);
             }
         }
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



 }
