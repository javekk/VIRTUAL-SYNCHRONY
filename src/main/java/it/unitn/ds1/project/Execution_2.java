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
 * - 3 nodes enter at the very beginning all together
 * - node5 enters after 5 seconds
 * - node3 will crash after sent the first message to node1, it tries to enter every 60 second, but it always crashes
 * - node5 crashes and it will recover in 25 seconds
 *
 */
public class Execution_2 {

    public static Logger logger = Logger.getLogger(Execution_2.class.getName());

    public static void main(String[] args) {

        //Prepare the log
        try {
            FileHandler fh;
            Handler handlerObj = new ConsoleHandler();
            handlerObj.setLevel(Level.ALL);
            logger.setLevel(Level.ALL);
            logger.setUseParentHandlers(false);

            // This block configure the logger with handler and formatter
            fh = new FileHandler("execution_1.log");
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
        ActorRef node3 = system.actorOf(
                NodeParticipant.props(),
                "Node20");
        ActorRef node4 = system.actorOf(
                NodeParticipant.props(),
                "Node30");
        ActorRef node5 = system.actorOf(
                NodeParticipant.props(),
                "Node40");

        try {
            coordinator.tell(new Node.JoinRequest(), node1);
            coordinator.tell(new Node.JoinRequest(), node3);
            coordinator.tell(new Node.JoinRequest(), node4);
            Thread.sleep(5000);
            coordinator.tell(new Node.JoinRequest(), node5);
            //even the coordinator has to start messaging
            coordinator.tell(new Node.StartChatMsg(), coordinator);

            Thread.sleep(5000);
            node5.tell(new NodeParticipant.Crash(25), null); //crash and recover in 60 sec



            System.in.read();
        }
        catch (Exception ioe) {
        }

        system.terminate();
    }

}

