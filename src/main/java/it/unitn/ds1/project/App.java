package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.ArrayList;
import java.util.List;

public class App {


    public static void main(String[] args) throws InterruptedException {

        // Create the 'helloakka' actor system
        final ActorSystem system = ActorSystem.create("helloakka");

        //the coordinator
        ActorRef coordinator = system.actorOf(
                NodeCoordinator.props(0),
                "Node0");

        ActorRef node1 = system.actorOf(
                NodeParticipant.props(),
                "Node1");
        ActorRef node2 = system.actorOf(
                NodeParticipant.props(),
                "Node2");

        coordinator.tell(new Node.JoinRequest(), node1);
        coordinator.tell(new Node.JoinRequest(), node2);
        coordinator.tell(new Node.StartChatMsg(), coordinator);

        try {

            /*
             * After a while I insert Node3 that
             * will crash after sending one message
             */
            Thread.sleep(10000);
            ActorRef node3 = system.actorOf(
                    NodeParticipant.props(), // this one will catch up the topic "a"
                    "Node3");
            coordinator.tell(new Node.JoinRequest(), node3);

            /*
             * After a while I insert Node4
             */
            Thread.sleep(10000);
            ActorRef node4 = system.actorOf(
                    NodeParticipant.props(), // this one will catch up the topic "a"
                    "Node4");
            coordinator.tell(new Node.JoinRequest(), node4);
            Thread.sleep(10000);
            //node4.tell(new NodeParticipant.Crash(20), null); //crash and recover in 60 sec

            System.in.read();


        }
        catch (Exception ioe) {}
        system.terminate();
    }

}

