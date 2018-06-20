package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import java.io.IOException;

public class App{

    public final static int N_INITIAL_PARTICIPANTS = 3;
    public final static int VOTE_TIMEOUT = 1000;      // timeout for the votes, ms
    public final static int DECISION_TIMEOUT = 2000;  // timeout for the decision, ms

    // the votes that the participants will send (for testing)
    final static Vote[] predefinedVotes =
            new Vote[] {Vote.YES, Vote.YES, Vote.YES}; // as many as N_PARTICIPANTS

    // Start message that sends the list of participants to everyone
    public static class StartMessage implements Serializable {
        public final List<ActorRef> group;    // an array of group members
        public StartMessage(List<ActorRef> group) {
            // Copying the group as an unmodifiable list
            this.group = Collections.unmodifiableList(new ArrayList<ActorRef>(group));
        }
    }

    public enum Vote {NO, YES};
    public enum Decision {ABORT, COMMIT};

    public static class VoteRequest implements Serializable {}
    public static class VoteResponse implements Serializable {
        public final Vote vote;
        public VoteResponse(Vote v) { vote = v; }
    }
    public static class DecisionRequest implements Serializable {}
    public static class DecisionResponse implements Serializable {
        public final Decision decision;
        public DecisionResponse(Decision d) { decision = d; }
    }


    public static class Timeout implements Serializable {}

    // a message that emulates a node restart
    public static class Recovery implements Serializable {}



    public static void main(String[] args) {

        // Create the actor system
        final ActorSystem system = ActorSystem.create("helloakka");

        // Create the coordinator
        ActorRef coordinator = system.actorOf(Coordinator.props(), "coordinator");

        // Create participants
        List<ActorRef> group = new ArrayList<>();
        for (int i=0; i<N_INITIAL_PARTICIPANTS; i++) {
            group.add(system.actorOf(Participant.props(i), "participant" + i));
        }

        // Send start messages to the participants to inform them of the group
        StartMessage start = new StartMessage(group);
        for (ActorRef peer: group) {
            peer.tell(start, null);
        }
        // Send the start messages to the coordinator
        coordinator.tell(start, null);

        try {
            System.out.println(">>> Press ENTER to exit <<<");
            System.in.read();
        }
        catch (IOException ioe) {}
        system.terminate();
    }
}
