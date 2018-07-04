package it.unitn.ds1.project;

import akka.actor.ActorRef;

import java.util.List;

public class View {

    /*
     * The list of peers (the multicast group)
     */
    private List<ActorRef> group;

    /*
     * The list of peers names (the multicast group)
     */
    private List<String> viewAsString;

    /*
     * Number of current view
     */
    private int viewCounter;

    public View(List<ActorRef> group, List<String> viewAsString, int viewCounter) {
        this.group = group;
        this.viewAsString = viewAsString;
        this.viewCounter = viewCounter;
    }

    public List<ActorRef> getGroup() {
        return group;
    }

    public void setGroup(List<ActorRef> group) {
        this.group = group;
    }

    public List<String> getViewAsString() {
        return viewAsString;
    }

    public void setViewAsString(List<String> viewAsString) {
        this.viewAsString = viewAsString;
    }

    public int getViewCounter() {
        return viewCounter;
    }

    public void setViewCounter(int viewCounter) {
        this.viewCounter = viewCounter;
    }
}
