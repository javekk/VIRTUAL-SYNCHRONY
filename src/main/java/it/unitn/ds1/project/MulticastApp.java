package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.io.IOException;

import it.unitn.ds1.project.Chatter.JoinGroupMsg;
import it.unitn.ds1.project.Chatter.StartChatMsg;
import it.unitn.ds1.project.Chatter.PrintHistoryMsg;

public class MulticastApp {


    public static void main(String[] args) {

        // Create the 'helloakka' actor system
        final ActorSystem system = ActorSystem.create("helloakka");

        List<ActorRef> group = new ArrayList<>();

        int id = 0;

        //the coordinator
        ActorRef coordinator = system.actorOf(
                NodeCoordinator.props(0),
                "Node0");
        group.add(coordinator);

        ActorRef node1 = system.actorOf(
                NodePartecipant.props(),
                "Node1");
        group.add(node1);

        ActorRef node2 = system.actorOf(
                NodePartecipant.props(),
                "Node2");
        group.add(node2);


        coordinator.tell(new Chatter.JoinRequest(), node1);
        coordinator.tell(new Chatter.JoinRequest(), node2);


        try {
            System.out.println("\n\n>>> Wait for the chats to stop and press ENTER <<<\n\n");

            /*
             * After a while I insert Node3
             */
            Thread.sleep(2000);
            ActorRef node3 = system.actorOf(
                    NodePartecipant.props(), // this one will catch up the topic "a"
                    "Node3");
            coordinator.tell(new Chatter.JoinRequest(), node3);
            group.add(node3);

            System.in.read();

            PrintHistoryMsg msg = new PrintHistoryMsg();
            for (ActorRef peer: group) {
                peer.tell(msg, null);
            }
            System.out.println("\n\n>>> Press ENTER to exit <<<\n\n");
            System.in.read();
        }
        catch (Exception ioe) {}
        system.terminate();
    }
}

