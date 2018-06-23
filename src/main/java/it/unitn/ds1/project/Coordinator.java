package it.unitn.ds1.project;

import akka.actor.ActorRef;
import akka.actor.Props;
import it.unitn.ds1.project.App;
import it.unitn.ds1.project.Node;

import java.util.HashSet;
import java.util.Set;

public class Coordinator extends Node {

    // here all the nodes that sent YES are collected
    public Set<ActorRef> yesVoters = new HashSet<>();

    boolean allVotedYes() { // returns true if all voted YES
        return yesVoters.size() >= App.N_INITIAL_PARTICIPANTS;
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
                .match(App.Recovery.class, this::onRecovery)
                .match(App.StartMessage.class, this::onStartMessage)
                .match(App.VoteResponse.class, this::onVoteResponse)
                .match(App.Timeout.class, this::onTimeout)
                .match(App.DecisionRequest.class, this::onDecisionRequest)
                .build();
    }

    public void onStartMessage(App.StartMessage msg) {                   /* Start */
        if (crashed) return;

        setGroup(msg);
        print("Sending vote request");
        multicast(new App.VoteRequest());
        //multicastAndCrash(new VoteRequest(), 3000);
        setTimeout(App.VOTE_TIMEOUT);
        //crash(5000);
    }

    public void onVoteResponse(App.VoteResponse msg) {                    /* Vote */
        if (crashed) return;

        if (hasDecided()) {
            // we have already decided and sent the decision to the group,
            // so do not care about other votes
            return;
        }
        App.Vote v = (msg).vote;
        if (v == App.Vote.YES) {
            yesVoters.add(getSender());
            if (allVotedYes()) {
                fixDecision(App.Decision.COMMIT);
                //if (id==-1) {crash(3000); return;}
                multicast(new App.DecisionResponse(decision));
                //multicastAndCrash(new DecisionResponse(decision), 3000);
            }
        }
        else { // a NO vote
            // on a single NO we decide ABORT
            fixDecision(App.Decision.ABORT);
            multicast(new App.DecisionResponse(decision));
        }
    }

    public void onTimeout(App.Timeout msg) {                           /* Timeout */
        if (crashed) return;
        if (!hasDecided()) {
            print("Timeout");
            fixDecision(App.Decision.ABORT);
            multicast(new App.DecisionResponse(decision));

        }
    }

    public void onRecovery(App.Recovery msg) {
        crashed = false;
        if (decision == App.Decision.ABORT || decision == App.Decision.COMMIT){
            multicast(new App.DecisionResponse(decision));
        } else{
            fixDecision(App.Decision.ABORT);
            multicast(new App.DecisionResponse(decision));
        }
    }
}