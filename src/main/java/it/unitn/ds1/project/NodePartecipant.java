package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class NodePartecipant extends Node {

    //         _   _               _            ____
    //        | \ | |   ___     __| |   ___    |  _ \   _ __    ___    _ __    ___
    //        |  \| |  / _ \   / _` |  / _ \   | |_) | | '__|  / _ \  | '_ \  / __|
    //        | |\  | | (_) | | (_| | |  __/   |  __/  | |    | (_) | | |_) | \__ \
    //        |_| \_|  \___/   \__,_|  \___|   |_|     |_|     \___/  | .__/  |___/
    //

    /*
     * This is the coordinator. id 0 default
     */
    public int coordinatorId;

    /*
     * String for log
     */
    public String out = "";


    /*
     * Actor Constructor
     */
    public NodePartecipant(){this.unstable = true;}
    static public Props props() {
        return Props.create(NodePartecipant.class, () -> new NodePartecipant());
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
                .match(JoinGroupMsg.class, this::onJoinGroupMsg)    //#1
                .match(StartChatMsg.class, this::onStartChatMsg)    //#2
                .match(ChatMsg.class, this::onChatMsg)         //#3
                .match(PrintHistoryMsg.class, this::printHistory)      //#4
                .match(NewView.class, this::onGetNewView)            //#5
                .match(CanJoin.class, this::join)                    //#6
                .match(NewId.class, this::onNewId)      //#8
                .match(Crash.class,    this::onCrash)      //#p1
                .match(Unstable.class,    this::onUnstable)      //#9
                .match(Flush.class, this::onFlush) //#10
                .build();
    }


    /*
     * #p1
     * I wanna crash
     */
    public static class Crash implements Serializable {
        public final int delay;
        public Crash(int delay) {
            this.delay = delay;
        }

    }

    //             _             _                                ____           _
    //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
    //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
    //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
    //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/


    /*
     * #3
     * When we received a Message
     * I get the last Message from the sender(to drop)
     * I replace the last message with the new one
     * I deliver/drop the Old Message
     */
    public void onChatMsg(ChatMsg msg) {

        if(crashed) return;

        System.out.println("\u001B[32m" + "Message \"" + msg.text + "\" " + "from Node: " + msg.senderId + " arrived to: " + this.id);

        ChatMsg drop;
        if (lastMessages.get(group.get(msg.senderId)) != null) {
            drop = lastMessages.get(group.get(msg.senderId));
            deliver(drop);

        }
        lastMessages.put(group.get(msg.senderId), msg);
    }


    /*
     * #5
     * When I receive a new view,
     * I am unstable
     * Multicast Unstable
     * Multicast Flush, with my last messages for the crashed nodes
     */
    public void onGetNewView(NewView newView) {

        if(newView.viewCounter < this.viewCounter) return;

        System.out.println("\u001B[33m" + "Node " + getSelf().path().name() + ". New view Arrived: " + newView.view.toString()); //add list of node inside view

        this.unstable = true;

        //Check for the crashed-peers' messages
        this.crashedNodesAndTheirMessages = this.checkForCrashedNodesMessages(newView);

        this.group = newView.group;
        this.view = newView.view;
        this.viewCounter = newView.viewCounter;

        multicast(new Unstable(newView));
        multicast(new Flush(newView, this.crashedNodesAndTheirMessages));

        this.crashedNodesAndTheirMessages.clear();
    }


    /*
     * #6
     * Let's join the group and start multicasting
     */
    public void join(CanJoin cj) {
        if(cj.newView.viewCounter > this.viewCounter){
            this.group = cj.newView.group;
            this.view = cj.newView.view;
            this.viewCounter = cj.newView.viewCounter;
        }
        this.getSelf().tell(new JoinGroupMsg(), null);
        this.getSelf().tell(new StartChatMsg(), getSelf());
        this.crashed = false;
    }


    /*
     * #8
     * I have a new ID, yea
     */
    public void onNewId(NewId newId) {
        this.id = newId.newId;
    }



    /*
     * #p1
     * emulate a crash and a recovery in a given time
     */
    public void onCrash(Crash c) throws InterruptedException {
        this.crashed = true;
        System.out.println("CRASH!!!!!!!" + getSelf().path().name());

        /*
         * I try to rejoin after a while
         */
        getContext().system().scheduler().scheduleOnce(
                Duration.create(c.delay, TimeUnit.SECONDS),
                this.group.get(0),
                new JoinRequest(), // the message to send
                getContext().system().dispatcher(), getSelf()
        );

    }

    /*
     * #10
     * On stable messages
     * I may received a flush with the new view, before receiving the new view
     *      |---> delete the old new view, it will arrive for sure, since the coordinator is reliable
     * Mark the sender as flush-received
     * Check if I did not receive the last message from the crashed nodes, and if not, deliver the last but one and hold the real last
     *
     */
    public void onFlush(Flush flush){

        if(flush.view.viewCounter < this.viewCounter) return;

        if(flush.view.viewCounter > this.viewCounter){
            this.crashedNodesAndTheirMessages = this.checkForCrashedNodesMessages(flush.view);

            this.group = flush.view.group;
            this.view = flush.view.view;
            this.viewCounter = flush.view.viewCounter;
            this.flushMessagesTracker.removeAll(this.flushMessagesTracker);
        }

        this.flushMessagesTracker.add(getSender());
        System.out.println("\u001B[32m Flush arrived from "+ getSender().path().name() + " to " + this.id +  " for view: "+ this.view.toString()); //add list of node inside view
        this.checkForLastMessages(flush.crashedNodesWithLastMessages); //if there were some crashes and I did not receive the last messages

        if((this.flushMessagesTracker.size()+1) == this.group.size()){
            //stable
            out = getSelf().path().name().substring(4) + " install view " + this.viewCounter + " " + this.view.toString() + "\n";
            System.out.println("\u001B[33m" + getSelf().path().name() + " INSTALL a new View: " + this.view.toString()); //add list of node inside view
            this.unstable = false;
            this.flushMessagesTracker.removeAll(this.flushMessagesTracker);
        }
    }



    //         _   _          _           _                     _____
    //        | | | |   ___  | |  _ __   (_)  _ __     __ _    |  ___|  _   _   _ __     ___   ___
    //        | |_| |  / _ \ | | | '_ \  | | | '_ \   / _` |   | |_    | | | | | '_ \   / __| / __|
    //        |  _  | |  __/ | | | |_) | | | | | | | | (_| |   |  _|   | |_| | | | | | | (__  \__ \
    //        |_| |_|  \___| |_| | .__/  |_| |_| |_|  \__, |   |_|      \__,_| |_| |_|  \___| |___/
    //                           |_|                  |___/


    //creates log file
    public void ParticipantLog(String text) {

        String FILENAME = "participant.log";

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


}
