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
 * - 4 nodes enter at the very beginning all together
 * - node20 and node40 crash after respectively 5 and 10 second, (while they're receiving multicasts)
 * - node20 recovers in 25 seconds
 * - node40 recovers in 20 seconds
 */
public class Execution_1 {

    public static Logger logger = Logger.getLogger(Execution_1.class.getName());

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

        ActorRef node10 = system.actorOf(
                NodeParticipant.props(),
                "Node10");
        ActorRef node20 = system.actorOf(
                NodeParticipant.props(),
                "Node20");
        ActorRef node30 = system.actorOf(
                NodeParticipant.props(),
                "Node30");
        ActorRef node40 = system.actorOf(
                NodeParticipant.props(),
                "Node40");

        try {
            coordinator.tell(new Node.JoinRequest(), node10);
            coordinator.tell(new Node.JoinRequest(), node20);
            coordinator.tell(new Node.JoinRequest(), node30);
            coordinator.tell(new Node.JoinRequest(), node40);
            //even the coordinator has to start messaging
            coordinator.tell(new Node.StartChatMsg(), coordinator);

            Thread.sleep(5000);
            node20.tell(new NodeParticipant.Crash(25), null); //crash and recover in 60 sec

            Thread.sleep(5000);
            node40.tell(new NodeParticipant.Crash(20), null); //crash and recover in 60 sec


            System.in.read();
        }
        catch (Exception ioe) {
        }

        system.terminate();
    }

}

