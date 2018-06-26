package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.Props;

import java.util.ArrayList;
import java.util.List;

public class NodeCoordinator extends Chatter {


    //         _   _               _            ____
    //        | \ | |   ___     __| |   ___    |  _ \   _ __    ___    _ __    ___
    //        |  \| |  / _ \   / _` |  / _ \   | |_) | | '__|  / _ \  | '_ \  / __|
    //        | |\  | | (_) | | (_| | |  __/   |  __/  | |    | (_) | | |_) | \__ \
    //        |_| \_|  \___/   \__,_|  \___|   |_|     |_|     \___/  | .__/  |___/
    //


    /*
     * id counter
     */
    private int idCounter = 0;

    public List<String> view;

    /*
     * Actor Constructor
     */
    NodeCoordinator(int id) {
        this.id = id;
        if(this.group == null){
            this.group = new ArrayList<>();
            this.group.add(getSelf());
            this.view = new ArrayList<>();
            this.view.add(getSelf().path().name());
        }
    }
    static Props props(int id) {
        return Props.create(NodeCoordinator.class, () -> new NodeCoordinator(id));
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
                .match(JoinRequest.class,    this::onJoinRequest)      //#6
                .build();
    }


    //             _             _                                ____           _
    //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
    //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
    //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
    //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/


    /*
     * #5
     * Coordinator has a different behavior for the same class
     */
    public void onJoinRequest(JoinRequest jr) {


        System.out.println("\u001B[34m" + getSender().path().name() + " asking for joining");
        /*
         * Send the new id to the new node
         */
        getSender().tell(new NewId(++idCounter), getSelf());
        this.view.add(getSender().path().name());

        /*
         * I send to anybody the new view with the new peer
         */
        System.out.println("\u001B[34m" + getSelf().path().name() + " sending new view with " + this.view.toString());
        this.group.add(getSender());
        this.multicast(new NewView(this.group));

        /*
         * Finally I can send the ok for enter to the requester
         */

        getSender().tell(new CanJoin(this.group), getSelf());

    }

}
