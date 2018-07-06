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
     * Guarantee the increase of the view counter
     */
    private int viewinit = 0;

    /*
     * Guarantee the right id generation
     */
    private int idinit = 0;

    /*
     *  Used for view generation
     *  If I change view quickly, I have to remember the last view I wanted to install(even if not yet install)
     */
    private View view_buffer;

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
                .match(JoinGroupMsg.class, this::onJoinGroupMsg)    //#1
                .match(StartChatMsg.class, this::onStartChatMsg)    //#2
                .match(ChatMsg.class, this::onChatMsg)         //#3
                .match(PrintHistoryMsg.class, this::printHistory)      //#4
                .match(JoinRequest.class, this::onJoinRequest)      //#6
                .match(Flush.class, this::onFlush)
                .match(HeartBeat.class, this::onHeartBeat)
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
     * #5
     * Coordinator has a different behavior for the same class
     */
    public void onJoinRequest(JoinRequest jr) {

        System.out.println("\u001B[34m" + getSender().path().name() + "-> asking for joining");

        if (this.view_buffer == null) {
            this.view_buffer = new View(this.view.group, this.view.viewAsString, this.view.viewCounter);
        }

        getSender().tell(new initNode(++this.idinit, this.view_buffer), getSelf());

        getSender().tell(new StartChatMsg(), getSelf());

        this.fromWhomTheMessagesArrived.put(getSender(), true);

        View newView = this.getNewView(getSender());

        multicastAllUnstableMessages(newView);
        multicast(newView, newView);
        multicast(new Flush(newView), newView);

    }


    private void onHeartBeat(HeartBeat hb){
        this.fromWhomTheMessagesArrived.put(getSender(), true);
    }


    private void crashDetector() {

        //If I find some false values, means that no messages arrived from that peers
        List<ActorRef> crashedPeers = new ArrayList<>();
        HashMap<ActorRef,Boolean> hm = new HashMap<>(this.fromWhomTheMessagesArrived);
        for (Map.Entry<ActorRef, Boolean> entry : hm.entrySet()) {
            ActorRef key = entry.getKey();
            boolean value = entry.getValue();
            if (!value){
                crashedPeers.add(key);
                this.fromWhomTheMessagesArrived.remove(key);
            }
        }

        if (!crashedPeers.isEmpty()) {
            //OMG! INDIGNAZIONE!!!!!
            System.out.println("OMG!!! Someone is crashed! ___look who's crashed->" + crashedPeers.toString());
            //create and send the new view
            View newView = this.getTheNewViewFromCrashed(crashedPeers);
            multicastAllUnstableMessages(newView);
            multicast(newView, newView);
            multicast(new Flush(newView), newView);
        }


        //Bring all to false, waiting for messages
        for (Map.Entry<ActorRef, Boolean> entry : this.fromWhomTheMessagesArrived.entrySet()) {
            ActorRef key = entry.getKey();
            this.fromWhomTheMessagesArrived.replace(key, false);
        }
    }


    /*
     * Method to create a new view
     */
    public View getNewView(ActorRef newPeer) {

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


    public View getTheNewViewFromCrashed(List<ActorRef> crashedNodes){

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
