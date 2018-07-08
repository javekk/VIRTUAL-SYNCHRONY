package it.unitn.ds1.project;


import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;


public class NodeParticipant extends Node {


    //Akka contructor
    static Props props() {
        return Props.create(NodeParticipant.class, NodeParticipant::new);
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
     */
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(StartChatMsg.class, this::onStartChatMsg)    //#1
                .match(ChatMsg.class, this::onChatMsg)              //#2
                .match(PrintHistoryMsg.class, this::printHistory)   //#3
                .match(View.class, this::onGetNewViewMessage)       //#5
                .match(initNode.class, this::onInit)                //#6
                .match(Flush.class, this::onFlush)                  //#7
                .match(Crash.class,    this::onCrash)               //#p1
                .build();
    }


    /*
     * #p1
     * When the node wants to crash
     */
    static class Crash implements Serializable {
        final int delay;
        Crash(int delay) {
            this.delay = delay;
        }
    }


    //             _             _                                ____           _
    //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
    //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
    //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
    //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/


    /*
     * #5
     * What a cohort does when receives a new view
     */
    public void onGetNewViewMessage(View view) {

        if(this.crashed) {
            return;
        }

        this.inhibit_sends++;

        //MODIFICATION: in this case, crash
        if(this.id == 2 && view.viewCounter == 3){
            getSelf().tell(new NodeParticipant.Crash(60), null);
            return;
        }

        //Multicast (and deliver) all the unstable message
        multicastAllUnstableMessages(view);

        //Flush the view
        multicast(new Flush(view), view);
    }


    /*
     * #6
     * Init, yea
     */
    private void onInit(initNode initNode) {
        this.crashed = false;             //if the node returns from a crash
        this.id = initNode.newId;         // new id, yea
        this.view = initNode.initialView; //in the init part

        //start sending heartbeats to the coordinator
        getContext().system().scheduler().schedule(
                Duration.create(100, TimeUnit.MILLISECONDS),
                Duration.create(4500, TimeUnit.MILLISECONDS),
                this::sendHeartBeats,
                getContext().system().dispatcher());
    }


    /*
     * #p1
     * emulate a crash and a recovery in a given time
     */
    private void onCrash(Crash c) {
        this.crashed = true;
        System.out.println("+++" + getSelf().path().name() + " just crashed+++"); //INDIGNAZIONE

        //Try to rejoin after a while
        getContext().system().scheduler().scheduleOnce(
                Duration.create(c.delay, TimeUnit.SECONDS),
                this.view.group.get(0),
                new JoinRequest(),
                getContext().system().dispatcher(), getSelf()
        );

    }


    //         _   _          _           _                     _____
    //        | | | |   ___  | |  _ __   (_)  _ __     __ _    |  ___|  _   _   _ __     ___   ___
    //        | |_| |  / _ \ | | | '_ \  | | | '_ \   / _` |   | |_    | | | | | '_ \   / __| / __|
    //        |  _  | |  __/ | | | |_) | | | | | | | | (_| |   |  _|   | |_| | | | | | | (__  \__ \
    //        |_| |_|  \___| |_| | .__/  |_| |_| |_|  \__, |   |_|      \__,_| |_| |_|  \___| |___/
    //                           |_|                  |___/


    /*
     * Send heartbeats to the coordinator
     */
    private void sendHeartBeats(){

        if (crashed || view == null) return;

        this.view.group.get(0).tell(new HeartBeat(), getSelf());
    }

}
