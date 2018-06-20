package it.unitn.ds1.project;


import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import scala.concurrent.duration.Duration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/*--------- Common functionality for both Coordinator and Particimants ------------*/
public abstract class Node extends AbstractActor {

    protected int id;                           // node ID
    protected List<ActorRef> participants;      // list of participant nodes
    protected App.Decision decision = null;     // decision taken by this node
    protected boolean crashed = false;          // simulates a crash

    public Node(int id) {
        super();
        this.id = id;
    }

    void setGroup(App.StartMessage sm) {
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
                new App.Recovery(), // message sent to myself
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
                new App.Timeout(), // the message to send
                getContext().system().dispatcher(), getSelf()
        );
    }

    // fix the final decision of the current node
    void fixDecision(App.Decision d) {
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

    public void onDecisionRequest(App.DecisionRequest msg) {  /* Decision Request */
        if (crashed) return;

        if (hasDecided())
            getSender().tell(new App.DecisionResponse(decision), getSelf());
        // just ignoring if we don't know the decision
    }
}