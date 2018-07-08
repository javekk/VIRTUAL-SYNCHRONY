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
 *@author Raffaele Perini
 *@author Giovanni Rafael Vuolo
 */
class Node extends AbstractActor {


    //         _   _               _            ____
    //        | \ | |   ___     __| |   ___    |  _ \   _ __    ___    _ __    ___
    //        |  \| |  / _ \   / _` |  / _ \   | |_) | | '__|  / _ \  | '_ \  / __|
    //        | |\  | | (_) | | (_| | |  __/   |  __/  | |    | (_) | | |_) | \__ \
    //        |_| \_|  \___/   \__,_|  \___|   |_|     |_|     \___/  | .__/  |___/
    //                                                                |_|

    //Current View
    View view;

    //Random Number used to random multicast
    private Random rnd = new Random();

    //number of sent messages
    private int sendCount = 0;

    //ID of the current actor
    int id = 0;

    //The local Buffer for last messages, in which we keep the ActorRef and its own last message
    private HashMap<ActorRef, ChatMsg> lastMessages = new HashMap<>();

    //The chat history
    private StringBuffer chatHistory = new StringBuffer();

    //Am I crashed?
    boolean crashed = false;

    //If 0, the node can send multicast messages
    int inhibit_sends = 0;

    //Buffer which holds the messages to deliver in views not yet installed
    private List<ChatMsg> messagesBuffer = new ArrayList<>();

    //List of received flush messages
    private List<Flush> flushBuffer = new ArrayList<>();

    //init param
    private boolean hasInstalledTheFirstView = false;


    //         __  __                   _____                                             ____   _
    //        |  \/  |  ___    __ _    |_   _|  _   _   _ __     ___   ___               / ___| | |   __ _   ___   ___    ___   ___
    //        | |\/| | / __|  / _` |     | |   | | | | | '_ \   / _ \ / __|    _____    | |     | |  / _` | / __| / __|  / _ \ / __|
    //        | |  | | \__ \ | (_| |     | |   | |_| | | |_) | |  __/ \__ \   |_____|   | |___  | | | (_| | \__ \ \__ \ |  __/ \__ \
    //        |_|  |_| |___/  \__, |     |_|    \__, | | .__/   \___| |___/              \____| |_|  \__,_| |___/ |___/  \___| |___/
    //                        |___/             |___/  |_|


    @Override
    public Receive createReceive() {
        // Empty mapping: we'll define it in the inherited classes
        return receiveBuilder().build();
    }


    /*
     * #1
     * Message that allows a node to start multicasting messages
     */
    static class StartChatMsg implements Serializable {
    }


    /*
     * #2
     * Chat message, a normal message
     */
    static class ChatMsg implements Serializable {
        final ActorRef actorRef;    // the sender of the message
        final String text;          // text of the message
        final int viewNumber;       // view in which the message belongs
        final int sequenceNumber;   // sequence number of the message
        final boolean isACopy;      // to say if we're dealing with unstable messages

        ChatMsg(ActorRef actorRef, String text, int viewNumber, int sequenceNumber, boolean isACopy) {
            this.actorRef = actorRef;
            this.text = text;
            this.viewNumber = viewNumber;
            this.sequenceNumber = sequenceNumber;
            this.isACopy = isACopy;
        }
    }


    /*
     * #3
     * Message to print the chat history
     */
    static class PrintHistoryMsg implements Serializable {
    }


    /*
     * #4
     * Message to ask to Coordinator for joining
     */
    static class JoinRequest implements Serializable {
    }


    /*
     * #5
     * View
     */
    static class View implements Serializable {
        final List<ActorRef> group;         //list of peers as actocRef
        final List<String> viewAsString;    //list of peers as string id
        final int viewCounter;              //id of the view

        View(List<ActorRef> group, List<String> viewAsString, int viewCounter) {
            this.group = group;
            this.viewAsString = viewAsString;
            this.viewCounter = viewCounter;
        }
    }


    /*
     * #6
     * Coordinator assigns the id
     * Sending also a view makes the implementation simpler
     */
    static class initNode implements Serializable {
        final int newId;
        final View initialView;

        initNode(int newId, View initialView) {
            this.newId = newId;
            this.initialView = initialView;
        }
    }


    /*
     * #7
     * Flush Message
     */
    static class Flush implements Serializable {
        final View view;

        Flush(View view) {
            this.view = view;
        }
    }


    /*
     * #8
     * HeartBeat
     */
    static class HeartBeat implements Serializable {
    }


    //             _             _                                ____           _
    //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
    //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
    //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
    //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/


    /*
     * #1
     * Schedule the multicast, with random intervals,
     * but always more than one second from one multicast to another
     */
    void onStartChatMsg(StartChatMsg msg) {

        getContext().system().scheduler().schedule(
                Duration.create((int) (5000 * Math.random()), TimeUnit.MILLISECONDS),
                Duration.create((int) (5000 * Math.random()) + 1000, TimeUnit.MILLISECONDS),
                this::sendChatMsg,
                getContext().system().dispatcher());
    }


    /*
     * #2
     * What the node does when receives a message
     */
    void onChatMsg(ChatMsg msg) {

        if (this instanceof NodeParticipant && !hasInstalledTheFirstView) {
            return; //before the init
        }

        if (crashed) {
            return;
        }

        System.out.println("\u001B[36m" + getSelf().path().name() + "-> ARRIVED " + msg.text + "; sender: " + msg.actorRef.path().name() + "; view " + msg.viewNumber); //add list of node inside view

        if (!msg.isACopy && msg.viewNumber == this.view.viewCounter) {
            // normal message
            if (lastMessages.get(getSender()) != null) {
                //according to the assumptions the previous message is stable, so deliver it
                deliver(lastMessages.get(getSender()));
            }
            lastMessages.put(getSender(), msg);
        } else if (msg.isACopy && msg.actorRef != getSelf()) {
            //is a copy(UNSTABLE) and it is not my msg
            if (msg.viewNumber == this.view.viewCounter) {
                //we are in the same view
                if (this.lastMessages.get(msg.actorRef) == null || msg.sequenceNumber > this.lastMessages.get(msg.actorRef).sequenceNumber) {
                    //we use the sequence number to check if the message is a duplicate
                    deliver(msg);
                    this.lastMessages.put(msg.actorRef, msg);
                }
            } else if (msg.viewNumber > this.view.viewCounter) {
                //save in the buffer
                messagesBuffer.add(msg);
            }
            //else if(msg.viewNumber < this.view.getViewCounter()) -> message is a duplicate, ignore it
        }
    }


    /*
     * #3
     * Print the History of this node
     */
    void printHistory(PrintHistoryMsg msg) {
        System.out.printf("%02d: %s\n", this.id, this.chatHistory);
    }


    /*
     * #7
     * What a node does when receives a flush message
     */
    void onFlush(Flush flush) {

        if (crashed) {
            return;
        }

        this.flushBuffer.add(flush);

        //number of flush needed to install the next view
        int numberOfFlushNeeded = Integer.MAX_VALUE;

        //the flush already holding to install the next view
        List<Flush> currentFlushesForTheNextView = new ArrayList<>();

        //init number of flush needed, doing intersection among all the views
        ArrayList<ActorRef> tmp = new ArrayList<>(this.flushBuffer.get(0).view.group);
        for (Flush f : this.flushBuffer) { tmp.retainAll(f.view.group); }
        numberOfFlushNeeded = tmp.size();

        //check for the flushes we have for the next view
        for (Flush f : this.flushBuffer) {
            if (f.view.viewCounter == this.view.viewCounter + 1) {
                currentFlushesForTheNextView.add(f);
            }
        }

        if (currentFlushesForTheNextView.size() == numberOfFlushNeeded - 1) {
            //here we have all the flushes needed to install the next view, so install it
            this.view = currentFlushesForTheNextView.get(0).view;
            this.flushBuffer.removeAll(currentFlushesForTheNextView); //remove the useless flushes

            //log
            System.out.println("\u001B[33m" + getSelf().path().name() + "-> INSTALL View" + this.view.viewCounter + ": " + this.view.viewAsString.toString()); //add list of node inside view
            String s = "0";
            for (String g : this.view.viewAsString) {
                if (!g.equals("0")) s = s.concat("," + g);
            }
            App.logger.info(getSelf().path().name().substring(4) + " install view " + this.view.viewCounter + " " + s);

            //check if there are messages to deliver in the view just install
            deliverBuffered();

            this.inhibit_sends--;
            this.hasInstalledTheFirstView = true;
        }
    }


    //         _   _          _           _                     _____
    //        | | | |   ___  | |  _ __   (_)  _ __     __ _    |  ___|  _   _   _ __     ___   ___
    //        | |_| |  / _ \ | | | '_ \  | | | '_ \   / _` |   | |_    | | | | | '_ \   / __| / __|
    //        |  _  | |  __/ | | | |_) | | | | | | | | (_| |   |  _|   | |_| | | | | | | (__  \__ \
    //        |_| |_|  \___| |_| | .__/  |_| |_| |_|  \__, |   |_|      \__,_| |_| |_|  \___| |___/
    //                           |_|                  |___/


    /*
     * Send the multicast
     */
    private void sendChatMsg() {

        if (crashed || (inhibit_sends > 0) || view == null) {return;}

        //update the sequence number (use as message id)
        this.sendCount++;

        App.logger.info(getSelf().path().name().substring(4) + " send multicast " + this.sendCount + " within " + this.view.viewCounter);

        //MODIFICATION
        // The node with id == 3, will deliver a message only to node1 and then crash
        if (this.id == 3) {
            System.out.println(" \u001B[31m" + getSelf().path().name() + " -> message in multicast -> text: " + "LOST_MESSAGE" + "; view: " + this.view.viewCounter);

            this.view.group.get(1).tell(new ChatMsg(getSelf(), "LOST_MESSAGE", this.view.viewCounter, this.sendCount, false), getSelf());
            getSelf().tell(new NodeParticipant.Crash(600), null);
            try {
                Thread.sleep(1000);
                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        //create a new message
        ChatMsg m = new ChatMsg(
                getSelf(),
                "[~" + numberToString((int) (Math.random() * 1000000) % 99999) + "]", //random text message, based on numbers
                this.view.viewCounter,
                this.sendCount,
                false);

        System.out.println(" \u001B[31m" + getSelf().path().name() + " -> message in multicast -> text: " + m.text + "; view: " + this.view.viewCounter);
        multicast(m, this.view); // multicast in the current view
        appendToHistory(m); // append the sent message
    }


    /*
     * We model random network delays with this code
     */
    void multicast(Serializable m, View view) {
        List<ActorRef> shuffledGroup = new ArrayList<>(view.group); //in the given view
        Collections.shuffle(shuffledGroup);
        for (ActorRef p : shuffledGroup) {
            if (!p.equals(getSelf())) { // not sending to self
                p.tell(m, getSelf());
                try {
                    Thread.sleep(rnd.nextInt(10));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    /*
     * Deliver the message, appeding it to the node's history
     */
    void deliver(ChatMsg m) {
        if (m != null && m.viewNumber == this.view.viewCounter) {
            //deliver only for the current view
            appendToHistory(m);
            System.out.println("\u001B[35m" + getSelf().path().name() + "-> DELIVER " + m.text + "; sender:" + m.actorRef.path().name() + "; view:" + m.viewNumber);
            App.logger.info(getSelf().path().name().substring(4) + " deliver multicast " + m.sequenceNumber + " from " + m.actorRef.path().name().substring(4) + " within " + m.viewNumber);
        }
    }


    /*
     * Append to the History
     */
    private void appendToHistory(ChatMsg m) {
        this.chatHistory.append("[" + m.actorRef.path().name() + "," + m.text + "]");
    }


    /*
     * According to the assumption, when a participant receives a multicast from an initiator P,
     * all previous multicasts from P are stable. So the last message is always UNSTable, if not
     * yet deliver. Here it is used "isCopy" param to know if the last message from P is been delivered or not.
     */
    void multicastAllUnstableMessages(View view) {

        for (Map.Entry<ActorRef, ChatMsg> entry : this.lastMessages.entrySet()) {
            ChatMsg unstableMsg = entry.getValue();

            if (unstableMsg != null && !unstableMsg.isACopy && unstableMsg.viewNumber < view.viewCounter) {

                System.out.println("     \u001B[31m" + getSelf().path().name() + " -> unstable message in multicast; sender:" + unstableMsg.actorRef.path().name() + "; text: " + unstableMsg.text + "; view: " + unstableMsg.viewNumber);
                ChatMsg m = new ChatMsg(unstableMsg.actorRef,
                        unstableMsg.text,
                        unstableMsg.viewNumber,
                        unstableMsg.sequenceNumber,
                        true); //I mark is as copy (means unstable)
                multicast(m, view);

                //Now the message is stable, so I can deliver it
                deliver(unstableMsg);
                // now m is a copy, so it could not be deliver more than once
                this.lastMessages.put(m.actorRef, m);
            }
        }
    }

    /*
     * Deliver messages in a view, that are been received in previous views
     */
    public void deliverBuffered() {
        for (ChatMsg msg : this.messagesBuffer) {
            if (msg.viewNumber == this.view.viewCounter) {
                deliver(msg);
                this.lastMessages.put(msg.actorRef, msg);
            }
        }
    }


    /*
     * Transform a Number to A String
     */
    private String numberToString(int number) {
        String ret = "";
        String digits = Integer.toString(number);
        for (int i = 0; i < digits.length(); i++) {
            try {
                ret = ret.concat(getCharForNumber(Integer.parseInt(digits.charAt(i) + "")));
            } catch (Exception e) {
                ret = ret.concat(Integer.toString((int) Math.random() * 10 % 4));
            }
        }
        return ret;
    }
    private String getCharForNumber(int i) {
        return i > 0 && i < 27 ? String.valueOf((char) (i + 64)) : null;
    }
}
