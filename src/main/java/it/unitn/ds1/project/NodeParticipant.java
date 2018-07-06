package it.unitn.ds1.project;


import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;


public class NodeParticipant extends Node {

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
    public NodeParticipant(){}
    static public Props props() {
        return Props.create(NodeParticipant.class, () -> new NodeParticipant());
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
                .match(View.class, this::onGetNewViewMessage)            //#5
                .match(initNode.class, this::onInit)      //#8
                .match(Flush.class, this::onFlush)
                .match(Crash.class,    this::onCrash)      //#p1
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
     * I get a new view
     */
    public void onGetNewViewMessage(View view) {

        this.inhibit_sends++;

        //Multicast (and deliver) all the unstable message
        multicastAllUnstableMessages(view);

        //Flush the view
        multicast(new Flush(view), view);
    }


    /*
     * #8
     * I have a new ID, yea
     */
    public void onInit(initNode initNode) {
        this.id = initNode.newId;
        this.view = initNode.initialView; //in the init part

        //start sending heartbeats to the coordinator
        getContext().system().scheduler().schedule(
                Duration.create(100, TimeUnit.MILLISECONDS),
                Duration.create(4500, TimeUnit.MILLISECONDS),
                () -> sendHeartBeats(),
                getContext().system().dispatcher());
    }


    /*
     * #p1
     * emulate a crash and a recovery in a given time
     */
    public void onCrash(Crash c) {
        this.crashed = true;
        System.out.println("+++" + getSelf().path().name() + " just crashed+++");

        /*
         * I try to rejoin after a while
         */
        getContext().system().scheduler().scheduleOnce(
                Duration.create(c.delay, TimeUnit.SECONDS),
                this.view.group.get(0),
                new JoinRequest(), // the message to send
                getContext().system().dispatcher(), getSelf()
        );

    }


    //         _   _          _           _                     _____
    //        | | | |   ___  | |  _ __   (_)  _ __     __ _    |  ___|  _   _   _ __     ___   ___
    //        | |_| |  / _ \ | | | '_ \  | | | '_ \   / _` |   | |_    | | | | | '_ \   / __| / __|
    //        |  _  | |  __/ | | | |_) | | | | | | | | (_| |   |  _|   | |_| | | | | | | (__  \__ \
    //        |_| |_|  \___| |_| | .__/  |_| |_| |_|  \__, |   |_|      \__,_| |_| |_|  \___| |___/
    //                           |_|                  |___/


    public void sendHeartBeats(){

        if (crashed || (inhibit_sends > 0) || view == null) return;

        this.view.group.get(0).tell(HeartBeat.class, getSelf());
    }

}
