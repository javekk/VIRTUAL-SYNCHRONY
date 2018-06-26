package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.Props;

import java.io.Serializable;

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
                .build();
    }



    //             _             _                                ____           _
    //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
    //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
    //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
    //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/



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
    }


    /*
     * #8
     * I have a new ID, yea
     */
    public void onNewId(NewId newId){
        this.id = newId.newId;
    }
}
