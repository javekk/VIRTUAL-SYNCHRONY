package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ArrayList;
import java.util.List;

public class App {


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
                NodeParticipant.props(),
                "Node1");
        group.add(node1);

        ActorRef node2 = system.actorOf(
                NodeParticipant.props(),
                "Node2");
        group.add(node2);


        coordinator.tell(new Node.JoinRequest(), node1);
        coordinator.tell(new Node.JoinRequest(), node2);
        coordinator.tell(new Node.StartChatMsg(), coordinator);

        try {
            System.out.println("\n\n>>> Wait for the chats to stop and press ENTER <<<\n\n");

            /*
             * After a while I insert Node3
             */
            Thread.sleep(10000);
            ActorRef node3 = system.actorOf(
                    NodeParticipant.props(), // this one will catch up the topic "a"
                    "Node3");
            coordinator.tell(new Node.JoinRequest(), node3);
            group.add(node3);

            /*
             * After a while I insert Node4
             */

            Thread.sleep(1000000);
            node3.tell(new NodeParticipant.Crash(60), null); //crash and recover in 60 sec


            System.in.read();


        }
        catch (Exception ioe) {}
        system.terminate();
    }

}

