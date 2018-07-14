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
 * - Normal execution on multicast, 4 nodes enter at the very beginning all together
 */
public class Execution_0 {

    public static Logger logger = Logger.getLogger(Execution_0.class.getName());

    public static void main(String[] args) {

        //Prepare the log
        try {
            FileHandler fh;
            Handler handlerObj = new ConsoleHandler();
            handlerObj.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);

            // This block configure the logger with handler and formatter
            fh = new FileHandler("execution_0.log");
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
                "Node10");
        ActorRef node2 = system.actorOf(
                NodeParticipant.props(),
                "Node20");
        ActorRef node3 = system.actorOf(
                NodeParticipant.props(),
                "Node30");
        ActorRef node4 = system.actorOf(
                NodeParticipant.props(),
                "Node40");

        try {
            coordinator.tell(new Node.JoinRequest(), node1);
            coordinator.tell(new Node.JoinRequest(), node2);
            coordinator.tell(new Node.JoinRequest(), node3);
            coordinator.tell(new Node.JoinRequest(), node4);
            //even the coordinator has to start messaging
            coordinator.tell(new Node.StartChatMsg(), coordinator);

            System.in.read();
        }
        catch (Exception ioe) {
        }

        system.terminate();
    }

}

