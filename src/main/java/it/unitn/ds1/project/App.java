package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Inbox;
import akka.actor.Cancellable;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.lang.Thread;
import java.lang.InterruptedException;
import java.util.Collections;
import java.util.Iterator;

import java.io.IOException;



public class App{

    final static int N_INITIAL_PARTICIPANTS = 3;
    final static int VOTE_TIMEOUT = 1000;      // timeout for the votes, ms
    final static int DECISION_TIMEOUT = 2000;  // timeout for the decision, ms

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



    /*
      _   _    ___    ____    _____
     | \ | |  / _ \  |  _ \  | ____|
     |  \| | | | | | | | | | |  _|
     | |\  | | |_| | | |_| | | |___
     |_| \_|  \___/  |____/  |_____|
     */
    /*--------- Common functionality for both Coordinator and Particimants ------------*/
    public abstract static class Node extends AbstractActor {

        protected int id;                           // node ID
        protected List<ActorRef> participants;      // list of participant nodes
        protected Decision decision = null;         // decision taken by this node
        protected boolean crashed = false;          // simulates a crash
        public Node(int id) {
            super();
            this.id = id;
        }
        void setGroup(StartMessage sm) {
            participants = new ArrayList<ActorRef>();
            for (ActorRef b: sm.group) {
                if (!b.equals(getSelf())) {
                    // copying all participant refs except for self
                    this.participants.add(b);
                }
            }
            print("starting with " + sm.group.size() + " peer(s)");
        }
        // emulate a crash and a recovery in a given time
        void crash(int recoverIn) {
            crashed = true;
            print("CRASH!!!");
            // setting a timer to "recover"
            getContext().system().scheduler().scheduleOnce(
                    Duration.create(recoverIn, TimeUnit.MILLISECONDS),
                    getSelf(),
                    new Recovery(), // message sent to myself
                    getContext().system().dispatcher(), getSelf()
            );
        }

        // emulate a delay of d milliseconds
        void delay(int d) {
            try {Thread.sleep(d);} catch (Exception e) {}
        }

        void multicast(Serializable m) {
            for (ActorRef p: participants)
                p.tell(m, getSelf());
        }

        // a multicast implementation that crashes after sending the first message
        void multicastAndCrash(Serializable m, int recoverIn) {
            for (ActorRef p: participants) {
                p.tell(m, getSelf());
                crash(recoverIn); return;
            }
        }

        // schedule a Timeout message in specified time
        void setTimeout(int time) {
            getContext().system().scheduler().scheduleOnce(
                    Duration.create(time, TimeUnit.MILLISECONDS),
                    getSelf(),
                    new Timeout(), // the message to send
                    getContext().system().dispatcher(), getSelf()
            );
        }

        // fix the final decision of the current node
        void fixDecision(Decision d) {
            if (!hasDecided()) {
                this.decision = d;
                print("decided " + d);
            }
        }

        boolean hasDecided() { return decision != null; } // has the node decided?

        // a simple logging function
        void print(String s) {
            System.out.format("%2d: %s\n", id, s);
        }

        @Override
        public Receive createReceive() {
            // Empty mapping: we'll define it in the inherited classes
            return receiveBuilder().build();
        }

        public void onDecisionRequest(DecisionRequest msg) {  /* Decision Request */
            if (crashed) return;

            if (hasDecided())
                getSender().tell(new DecisionResponse(decision), getSelf());
            // just ignoring if we don't know the decision
        }
    }


    /*
       ____                              _   _                   _
      / ___|   ___     ___    _ __    __| | (_)  _ __     __ _  | |_    ___    _ __
     | |      / _ \   / _ \  | '__|  / _` | | | | '_ \   / _` | | __|  / _ \  | '__|
     | |___  | (_) | | (_) | | |    | (_| | | | | | | | | (_| | | |_  | (_) | | |
      \____|  \___/   \___/  |_|     \__,_| |_| |_| |_|  \__,_|  \__|  \___/  |_|
     */
    public static class Coordinator extends Node {

        // here all the nodes that sent YES are collected
        private Set<ActorRef> yesVoters = new HashSet<>();

        boolean allVotedYes() { // returns true if all voted YES
            return yesVoters.size() >= N_INITIAL_PARTICIPANTS;
        }

        public Coordinator() {
            super(-1); // the coordinator has the id -1
        }

        static public Props props() {
            return Props.create(Coordinator.class, () -> new Coordinator());
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(Recovery.class, this::onRecovery)
                    .match(StartMessage.class, this::onStartMessage)
                    .match(VoteResponse.class, this::onVoteResponse)
                    .match(Timeout.class, this::onTimeout)
                    .match(DecisionRequest.class, this::onDecisionRequest)
                    .build();
        }

        public void onStartMessage(StartMessage msg) {                   /* Start */
            if (crashed) return;

            setGroup(msg);
            print("Sending vote request");
            multicast(new VoteRequest());
            //multicastAndCrash(new VoteRequest(), 3000);
            setTimeout(VOTE_TIMEOUT);
            //crash(5000);
        }

        public void onVoteResponse(VoteResponse msg) {                    /* Vote */
            if (crashed) return;

            if (hasDecided()) {
                // we have already decided and sent the decision to the group,
                // so do not care about other votes
                return;
            }
            Vote v = (msg).vote;
            if (v == Vote.YES) {
                yesVoters.add(getSender());
                if (allVotedYes()) {
                    fixDecision(Decision.COMMIT);
                    //if (id==-1) {crash(3000); return;}
                    multicast(new DecisionResponse(decision));
                    //multicastAndCrash(new DecisionResponse(decision), 3000);
                }
            }
            else { // a NO vote
                // on a single NO we decide ABORT
                fixDecision(Decision.ABORT);
                multicast(new DecisionResponse(decision));
            }
        }

        public void onTimeout(Timeout msg) {                           /* Timeout */
            if (crashed) return;
            if (!hasDecided()) {
                print("Timeout");
                fixDecision(Decision.ABORT);
                multicast(new DecisionResponse(decision));

            }
        }

        public void onRecovery(Recovery msg) {
            crashed = false;
            if (decision == Decision.ABORT || decision == Decision.COMMIT){
                multicast(new DecisionResponse(decision));
            } else{
                fixDecision(Decision.ABORT);
                multicast(new DecisionResponse(decision));
            }
        }
    }


    /*
      ____                   _                   _                           _
     |  _ \    __ _   _ __  | |_    ___    ___  (_)  _ __     __ _   _ __   | |_
     | |_) |  / _` | | '__| | __|  / _ \  / __| | | | '_ \   / _` | | '_ \  | __|
     |  __/  | (_| | | |    | |_  |  __/ | (__  | | | |_) | | (_| | | | | | | |_
     |_|      \__,_| |_|     \__|  \___|  \___| |_| | .__/   \__,_| |_| |_|  \__|
                                                    |_|
     */

    public static class Participant extends Node {
        ActorRef coordinator;
        public Participant(int id) { super(id); }

        static public Props props(int id) {
            return Props.create(Participant.class, () -> new Participant(id));
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(StartMessage.class, this::onStartMessage)
                    .match(VoteRequest.class, this::onVoteRequest)
                    .match(DecisionRequest.class, this::onDecisionRequest)
                    .match(DecisionResponse.class, this::onDecisionResponse)
                    .match(Timeout.class, this::onTimeout)
                    .match(Recovery.class, this::onRecovery)
                    .build();
        }

        public void onStartMessage(StartMessage msg) {                   /* Start */
            if (crashed) return;

            setGroup(msg);
        }

        public void onVoteRequest(VoteRequest msg) {                      /* Vote */
            if (crashed) return;

            this.coordinator = getSender();
            //if (id==2) {crash(5000); return;}    // simulate a crash
            //if (id==2) delay(4000);              // simulate a delay
            if (predefinedVotes[this.id] == Vote.NO) {
                fixDecision(Decision.ABORT);
            }
            print("sending vote " + predefinedVotes[this.id]);
            this.coordinator.tell(new VoteResponse(predefinedVotes[this.id]), getSelf());
            setTimeout(DECISION_TIMEOUT);
        }

        public void onTimeout(Timeout msg) {                           /* Timeout */
            if (crashed) return;
            if (!hasDecided()) {
                print("Timeout. Asking around.");
                if (predefinedVotes[this.id] == Vote.YES) {

                    coordinator.tell(new DecisionRequest(), getSelf());
                    multicast(new DecisionRequest());
                    setTimeout(DECISION_TIMEOUT);

                } else {
                    fixDecision(Decision.ABORT);
                }
                // TODO 3: termination protocol
                // ask other participants and the coordinator
            }
        }

        public void onRecovery(Recovery msg) {                        /* Recovery */
            crashed = false;
            if (!hasDecided()) {
                print("Recovery. Asking the coordinator.");
                coordinator.tell(new DecisionRequest(), getSelf());
                setTimeout(DECISION_TIMEOUT);
            }
        }

        public void onDecisionResponse(DecisionResponse msg) { /* Decision Response */
            if (crashed) return;
            // store the decision
            fixDecision(msg.decision);
        }
    }


    
     /*
                :::   :::           :::        :::::::::::       ::::    :::
              :+:+: :+:+:        :+: :+:          :+:           :+:+:   :+:
            +:+ +:+:+ +:+      +:+   +:+         +:+           :+:+:+  +:+
           +#+  +:+  +#+     +#++:++#++:        +#+           +#+ +:+ +#+
          +#+       +#+     +#+     +#+        +#+           +#+  +#+#+#
         #+#       #+#     #+#     #+#        #+#           #+#   #+#+#
        ###       ###     ###     ###    ###########       ###    ####
    */
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
