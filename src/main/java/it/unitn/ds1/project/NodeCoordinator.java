package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class NodeCoordinator extends Node {


    //         _   _               _            ____
    //        | \ | |   ___     __| |   ___    |  _ \   _ __    ___    _ __    ___
    //        |  \| |  / _ \   / _` |  / _ \   | |_) | | '__|  / _ \  | '_ \  / __|
    //        | |\  | | (_) | | (_| | |  __/   |  __/  | |    | (_) | | |_) | \__ \
    //        |_| \_|  \___/   \__,_|  \___|   |_|     |_|     \___/  | .__/  |___/
    //


    /*
     * HashMap Used for detect the crashes
     */
    private HashMap<ActorRef, Boolean> fromWhomTheMessagesArrived;

    /*
     * Actor Constructor
     */
    NodeCoordinator(int id) {

        this.id = id;

        List<ActorRef> group = new ArrayList<>();
        group.add(getSelf());
        List<String> viewAsString = new ArrayList<>(0);
        viewAsString.add(getSelf().path().name().substring(4));

        this.view = new View(group, viewAsString, 0);

        this.fromWhomTheMessagesArrived = new HashMap<>();

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
                .match(Unstable.class,    this::onUnstable)      //#9
                .build();
    }


    //             _             _                                ____           _
    //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
    //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
    //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
    //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/


    /*
     * Crash detection every 15 sec
     */


    @Override
    public void preStart() {
        getContext().system().scheduler().schedule(
                Duration.create(10, TimeUnit.SECONDS),
                Duration.create(15, TimeUnit.SECONDS),
                () -> crashDetector(),
                getContext().system().dispatcher());

    }




    /*
     * #3
     * When we received a Message
     * I get the last Message from the sender(to drop)
     * I replace the last message with the new one
     * I set that I received the message
     * I deliver/drop the Old Message
     */
    public void onChatMsg(ChatMsg msg) {

        ChatMsg drop;
        if (lastMessages.get(getSender()) != null) {
            drop = lastMessages.get(getSender());
            deliver(drop);
        }
        lastMessages.put(getSender(), msg);
        this.fromWhomTheMessagesArrived.replace(getSender(), true);

    }


    /*
     * #5
     * Coordinator has a different behavior for the same class
     */
    public void onJoinRequest(JoinRequest jr) {


        System.out.println("\u001B[34m" + getSender().path().name() + " asking for joining");
        /*
         * Send the new id to the new node
         */
        int id = view.getGroup().size() == 0 ? 0 : view.getGroup().size();
        getSender().tell(new NewId(id), getSelf());



    }





    private void crashDetector(){

        if(this.noMulticastStatus) return;

        /*
         * If I find some false values, means that no messages arrived from that peers
         */
        List<ActorRef> crashedPeers = new ArrayList<>();
        for(Map.Entry<ActorRef, Boolean> entry : this.fromWhomTheMessagesArrived.entrySet()){
            ActorRef key = entry.getKey();
            Boolean value = entry.getValue();
            if(!value) crashedPeers.add(key);
        }

        /*
         * OMG! INDIGNAZIONE!!!!!
         */
        if(!crashedPeers.isEmpty()){

            System.out.println("OMG!!! Someone is crashed! ___look who's crashed->" + this.fromWhomTheMessagesArrived.toString());


        }


        /*
         * Bring all to false, waiting for messages
         */
        for(Map.Entry<ActorRef, Boolean> entry : this.fromWhomTheMessagesArrived.entrySet()){
            ActorRef key = entry.getKey();
            this.fromWhomTheMessagesArrived.replace(key, false);
        }

    }




}
