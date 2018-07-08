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

    //Guarantee the right id generation
    private int idinit = 0;

    //Used in the view generation:
    //If views change quickly, I have to remember the last view coordinator wanted to install
    private View view_buffer;

    //Buffer used for detect the crashes
    private HashMap<ActorRef, Boolean> fromWhomTheMessagesArrived  = new HashMap<>();

    //Actor Constructor
    NodeCoordinator(int id) {
        this.id = id;

        //init the view
        List<ActorRef> group = new ArrayList<>();
        group.add(getSelf());
        List<String> viewAsString = new ArrayList<>(0);
        viewAsString.add(getSelf().path().name().substring(4));
        this.view = new View(group, viewAsString, 0);
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
                .match(StartChatMsg.class, this::onStartChatMsg)    //#1
                .match(ChatMsg.class, this::onChatMsg)              //#2
                .match(PrintHistoryMsg.class, this::printHistory)   //#3
                .match(JoinRequest.class, this::onJoinRequest)      //#4
                .match(Flush.class, this::onFlush)                  //#7
                .match(HeartBeat.class, this::onHeartBeat)          //#8
                .build();
    }


    //             _             _                                ____           _
    //            / \      ___  | |_    ___    _ __              | __ )    ___  | |__     __ _  __   __
    //           / _ \    / __| | __|  / _ \  | '__|    _____    |  _ \   / _ \ | '_ \   / _` | \ \ / /
    //          / ___ \  | (__  | |_  | (_) | | |      |_____|   | |_) | |  __/ | | | | | (_| |  \ V /
    //         /_/   \_\  \___|  \__|  \___/  |_|                |____/   \___| |_| |_|  \__,_|   \_/


    /*
     * Crash detection every 10 sec
     */
    @Override
    public void preStart() {

        getContext().system().scheduler().schedule(
                Duration.create(5, TimeUnit.SECONDS),
                Duration.create(10, TimeUnit.SECONDS),
                this::crashDetector,
                getContext().system().dispatcher());
    }


    /*
     * #4
     * What the coordinator does when a node asks to join
     */
    public void onJoinRequest(JoinRequest jr) {

        System.out.println("\u001B[34m" + getSender().path().name() + "-> asking for joining");

        if (this.view_buffer == null) {
            this.view_buffer = new View(this.view.group, this.view.viewAsString, this.view.viewCounter);
        }

        //init the node with the initial info
        getSender().tell(new initNode(++this.idinit, this.view_buffer), getSelf());

        //tell it to start messaging, it will start after install the right view
        getSender().tell(new StartChatMsg(), getSelf());

        //since it just ask for joining, mark it as not-crashed
        this.fromWhomTheMessagesArrived.put(getSender(), true);

        this.inhibit_sends++;

        //create new view with the new node
        View newView = this.getNewView(getSender());

        //the name is pretty self-explained
        multicastAllUnstableMessages(newView);

        System.out.println("  \u001B[31m" + getSelf().path().name() +" -> multicast view" + newView.viewCounter + ":" + newView.viewAsString.toString());

        //multicast new view with the flush
        multicast(newView, newView);
        multicast(new Flush(newView), newView);
    }


    /*
     * #8
     * When the coordinator receives an heartbeat, marks the sender as alive
     */
    private void onHeartBeat(HeartBeat hb){
        this.fromWhomTheMessagesArrived.put(getSender(), true);
    }


    //         _   _          _           _                     _____
    //        | | | |   ___  | |  _ __   (_)  _ __     __ _    |  ___|  _   _   _ __     ___   ___
    //        | |_| |  / _ \ | | | '_ \  | | | '_ \   / _` |   | |_    | | | | | '_ \   / __| / __|
    //        |  _  | |  __/ | | | |_) | | | | | | | | (_| |   |  _|   | |_| | | | | | | (__  \__ \
    //        |_| |_|  \___| |_| | .__/  |_| |_| |_|  \__, |   |_|      \__,_| |_| |_|  \___| |___/
    //                           |_|                  |___/


    /*
     * Check for crashed nodes
     */
    private void crashDetector() {

        //If I find some false values, means that no messages arrived from that peers
        List<ActorRef> crashedPeers = new ArrayList<>();
        HashMap<ActorRef,Boolean> hm = new HashMap<>(this.fromWhomTheMessagesArrived);
        for (Map.Entry<ActorRef, Boolean> entry : hm.entrySet()) {
            ActorRef key = entry.getKey();
            boolean value = entry.getValue();
            if (!value){
                crashedPeers.add(key);
                this.fromWhomTheMessagesArrived.remove(key); //it crashed, so I do not have to wait for the heartbeat
            }
        }

        if (!crashedPeers.isEmpty()) {
            //someone is crashed

            this.inhibit_sends++;

            System.out.println("OMG!!! Someone is crashed! ___look who's crashed->" + crashedPeers.toString());

            //create and send the new view
            View newView = this.getTheNewViewFromCrashed(crashedPeers);

            System.out.println("  \u001B[31m" + getSelf().path().name() +" -> multicast view" + newView.viewCounter + ":" + newView.viewAsString.toString());

            //create new view with the new node
            multicast(newView, newView);

            //multicast new view with the flush
            multicastAllUnstableMessages(newView);
            multicast(new Flush(newView), newView);
        }


        //Bring all to false, waiting for messages
        for (Map.Entry<ActorRef, Boolean> entry : this.fromWhomTheMessagesArrived.entrySet()) {
            ActorRef key = entry.getKey();
            this.fromWhomTheMessagesArrived.replace(key, false);
        }
    }


    /*
     * Method to create a new view, adding a new peer
     */
    private View getNewView(ActorRef newPeer) {

        if (this.view_buffer == null) {
            this.view_buffer = new View(this.view.group, this.view.viewAsString, this.view.viewCounter);
        }

        List<ActorRef> actors = new ArrayList<>(this.view_buffer.group);
        actors.add(newPeer);

        List<String> names = new ArrayList<>(this.view_buffer.viewAsString);
        names.add(newPeer.path().name().substring(4));

        int i = this.view_buffer.viewCounter + 1;
        View v = new View(actors, names, i);

        this.view_buffer = v;

        return v;
    }

    /*
     * Method to create a new view, removing the crashed nodes
     */
    private View getTheNewViewFromCrashed(List<ActorRef> crashedNodes){

        if (this.view_buffer == null) {
            this.view_buffer = new View(this.view.group, this.view.viewAsString, this.view.viewCounter);
        }

        List<ActorRef> actors = new ArrayList<>(this.view_buffer.group);
        actors.removeAll(crashedNodes);

        List<String> names = new ArrayList<>(this.view_buffer.viewAsString);
        for(ActorRef peer :crashedNodes){
            names.remove(peer.path().name().substring(4));
        }

        int i = this.view_buffer.viewCounter + 1;
        View v = new View(actors, names, i);

        this.view_buffer = v;

        return v;
    }
}
