package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class NodePartecipant extends Chatter {

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
     * Actor Constructor
     */

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
                .match(JoinGroupMsg.class,    this::onJoinGroupMsg)    //#1
                .match(StartChatMsg.class,    this::onStartChatMsg)    //#2
                .match(ChatMsg.class,         this::onChatMsg)         //#3
                .match(PrintHistoryMsg.class, this::printHistory)      //#4
                .match(NewView.class, this::onGetNewView)            //#5
                .match(CanJoin.class, this::join)                    //#6
                .match(NewId.class,    this::onNewId)      //#8
                .match(Crash.class,    this::onCrash)      //#8
                .build();
    }



    /*
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

        ChatMsg drop;
        if (lastMessages.get(group.get(msg.senderId)) != null) {
            drop = lastMessages.get(group.get(msg.senderId));
            System.out.println("\u001B[32m" + "Message \"" + drop.text + "\" " + "from Node: " + msg.senderId + " dropped by Node: " + this.id);

        }
        lastMessages.put(group.get(msg.senderId), msg);
        deliver(msg);
    }





    /*
     * #5
     * When I receive a new view, I install it, easy
     */
    public void onGetNewView(NewView newView){

        //need to check if old view messages are delivered
        //need to check all FLUSH messages are received by all
        this.group = newView.group;
        this.view = newView.view;
        System.out.println( "\u001B[33" +
                "m" + "Node " + getSelf().path().name() + " Install a new View: " + this.view.toString()); //add list of node inside view

    }


    /*
     * #6
     * Let's join the group and start multicasting
     */
    public void join(CanJoin cj){
        JoinGroupMsg join = new JoinGroupMsg(cj.group);
        this.getSelf().tell(join, null);
        this.getSelf().tell(new StartChatMsg(), getSelf());
        this.crashed = false;
    }


    /*
     * #8
     * I have a new ID, yea
     */
    public void onNewId(NewId newId){
        this.id = newId.newId;
    }




    // emulate a crash and a recovery in a given time
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
}
