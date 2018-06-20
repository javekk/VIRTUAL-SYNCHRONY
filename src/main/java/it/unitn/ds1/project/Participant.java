package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.Props;

public class Participant extends Node {

    ActorRef coordinator;
    public Participant(int id) { super(id); }

    static public Props props(int id) {
        return Props.create(Participant.class, () -> new Participant(id));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(App.StartMessage.class, this::onStartMessage)
                .match(App.VoteRequest.class, this::onVoteRequest)
                .match(App.DecisionRequest.class, this::onDecisionRequest)
                .match(App.DecisionResponse.class, this::onDecisionResponse)
                .match(App.Timeout.class, this::onTimeout)
                .match(App.Recovery.class, this::onRecovery)
                .build();
    }

    public void onStartMessage(App.StartMessage msg) {                   /* Start */
        if (crashed) return;

        setGroup(msg);
    }

    public void onVoteRequest(App.VoteRequest msg) {                      /* Vote */
        if (crashed) return;

        this.coordinator = getSender();
        //if (id==2) {crash(5000); return;}    // simulate a crash
        //if (id==2) delay(4000);              // simulate a delay
        if (App.predefinedVotes[this.id] == App.Vote.NO) {
            fixDecision(App.Decision.ABORT);
        }
        print("sending vote " + App.predefinedVotes[this.id]);
        this.coordinator.tell(new App.VoteResponse(App.predefinedVotes[this.id]), getSelf());
        setTimeout(App.DECISION_TIMEOUT);
    }

    public void onTimeout(App.Timeout msg) {                           /* Timeout */
        if (crashed) return;
        if (!hasDecided()) {
            print("Timeout. Asking around.");
            if (App.predefinedVotes[this.id] == App.Vote.YES) {

                coordinator.tell(new App.DecisionRequest(), getSelf());
                multicast(new App.DecisionRequest());
                setTimeout(App.DECISION_TIMEOUT);

            } else {
                fixDecision(App.Decision.ABORT);
            }
        }
    }

    public void onRecovery(App.Recovery msg) {                        /* Recovery */
        crashed = false;
        if (!hasDecided()) {
            print("Recovery. Asking the coordinator.");
            coordinator.tell(new App.DecisionRequest(), getSelf());
            setTimeout(App.DECISION_TIMEOUT);
        }
    }

    public void onDecisionResponse(App.DecisionResponse msg) { /* Decision Response */
        if (crashed) return;
        // store the decision
        fixDecision(msg.decision);
    }
}