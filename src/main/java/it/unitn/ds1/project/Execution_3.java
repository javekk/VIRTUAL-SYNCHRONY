package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.io.IOException;
import java.util.logging.*;

/*
 * @author Raffaele Perini
 * @author Giovanni Rafael Vuolo
 *
 * NB: name nodes as NodeID, with ID = integer
 *
 * Nodes join/crash flow:
 * - Node0 is the coordinator
 * - Node1 and Node2 join
 * - Node3 joins after 10 seconds
 * - Node2 crashes when receives the view 3 (# crash after receiving view change)
 * - Node4 join while the system is waiting for node2
 * - Node3 crashes while is multicasting (# crash during sending multicast)
 * - Node4 crashes after 10 seconds (# crash after receiving multicast)
 * - Node4 recover after 25 seconds
 * - (node3 recovers and crashes every 600 sec)
 */
public class Execution_3 {

    public static Logger logger = Logger.getLogger(Execution_3.class.getName());

    public static void main(String[] args) {

        //Prepare the log
        try {
            FileHandler fh;
            Handler handlerObj = new ConsoleHandler();
            handlerObj.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);

            // This block configure the logger with handler and formatter
            fh = new FileHandler("project.log");
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);

            logger.info("Start Log");

        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

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

        //node1 and node2 ask for join
        coordinator.tell(new Node.JoinRequest(), node1);
        coordinator.tell(new Node.JoinRequest(), node2);
        //even the coordinator has to start messaging
        coordinator.tell(new Node.StartChatMsg(), coordinator);

        try {

            // After a while I insert Node3 that will crash after sending one message
            Thread.sleep(10000);
            ActorRef node3 = system.actorOf(
                    NodeParticipant.props(),
                    "Node3");
            coordinator.tell(new Node.JoinRequest(), node3);

            //After a while I insert Node4 that will crash
            Thread.sleep(10000);
            ActorRef node4 = system.actorOf(
                    NodeParticipant.props(),
                    "Node4");
            coordinator.tell(new Node.JoinRequest(), node4);
            Thread.sleep(10000);
            node4.tell(new NodeParticipant.Crash(25), null); //crash and recover in 60 sec

            System.in.read();
        }
        catch (Exception ioe) {
        }

        system.terminate();
    }

}

