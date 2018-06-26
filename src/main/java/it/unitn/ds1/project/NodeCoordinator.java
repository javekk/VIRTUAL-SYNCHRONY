package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.Props;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

    /*
     * list of names in view
     *View
     */
    public List<String> view;

    /*
     * number of view
     */
    public int viewCounter = 0;

    /*
     * HashMap Used for detect the crashes
     */
    private HashMap<ActorRef, Boolean> fromWhomTheMessagesArrived;

    /*
     * Actor Constructor
     */
    NodeCoordinator(int id) {
        this.id = id;
        if(this.group == null){
            this.group = new ArrayList<>();
            this.group.add(getSelf());

            this.view = new ArrayList<>();
            this.view.add(getSelf().path().name().substring(4));
            this.fromWhomTheMessagesArrived = new HashMap<>();

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
        if (lastMessages.get(group.get(msg.senderId)) != null) {
            drop = lastMessages.get(group.get(msg.senderId));
            System.out.println("\u001B[32m" + "Message \"" + drop.text + "\" " + "from Node: " + msg.senderId + " dropped by Node: " + this.id);

        }
        lastMessages.put(group.get(msg.senderId), msg);
        this.fromWhomTheMessagesArrived.replace(getSender(), true);
        deliver(msg);
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
        int id = group.size() == 0 ? 0 : group.size();
        getSender().tell(new NewId(id), getSelf());

        /*
         * Add new node to the view
         */
        this.view.add(getSender().path().name().substring(4));

        /*
         * Increase view number
         */
        this.viewCounter++;

        /*
         * I send to anybody the new view with the new peer
         */
        System.out.println("\u001B[34m" + getSelf().path().name() + " sending new view with " + this.view.toString());
        this.group.add(getSender());
        this.fromWhomTheMessagesArrived.put(getSender(), true);
        this.multicast(new NewView(this.group, this.view, this.viewCounter));


        /*
         * Finally I can send the ok for enter to the requester
         */

        getSender().tell(new CanJoin(this.group), getSelf());

    }


    private void crashDetector(){

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
            System.out.println("OMG!!! Someone is crashed!!11!! INDIGNAZIONE!!!  ___loro?->" + this.fromWhomTheMessagesArrived.toString());
            /*
             * SOLUZIONE TEMPORANEA LI TOLGO E BOM
             */
             for(ActorRef a :crashedPeers){
                this.fromWhomTheMessagesArrived.remove(a);
                this.group.remove(a);
                for(int i = 0; i < view.size(); i++){
                    if(view.get(i).equals(a.path().name().substring(4))) view.remove(i);
                }
             }

             viewCounter++;
             this.multicast(new NewView(this.group, this.view, this.viewCounter));
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
