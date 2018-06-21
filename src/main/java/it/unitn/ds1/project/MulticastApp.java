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

        // the first four peers will be participating in conversations
        group.add(system.actorOf(
                Chatter.props(id++),  // this one will start the topic "a"
                "chatter0_Mario"));

        group.add(system.actorOf(
                Chatter.props(id++), // this one will catch up the topic "a"
                "chatter1_Sandrone"));

        group.add(system.actorOf(
                Chatter.props(id++),  // this one will start the topic "a"
                "chatter2_Gianno"));

        group.add(system.actorOf(
                Chatter.props(id++), // this one will catch up the topic "a"
                "chatter3_Sale"));

        // ensure that no one can modify the group
        group = Collections.unmodifiableList(group);

        // send the group member list to everyone in the group
        JoinGroupMsg join = new JoinGroupMsg(group);
        for (ActorRef peer: group) {
            peer.tell(join, null);
        }

        group.get(0).tell(new StartChatMsg(), null);

        try {
            System.out.println("\n\n>>> Wait for the chats to stop and press ENTER <<<\n\n");
            System.in.read();

            PrintHistoryMsg msg = new PrintHistoryMsg();
            for (ActorRef peer: group) {
                peer.tell(msg, null);
            }
            System.out.println("\n\n>>> Press ENTER to exit <<<\n\n");
            System.in.read();
        }
        catch (IOException ioe) {}
        system.terminate();
    }
}

