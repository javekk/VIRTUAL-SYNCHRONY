package it.unitn.ds1.project;


import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import java.util.Random;
import java.io.Serializable;
import akka.actor.Props;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.lang.Thread;
import java.lang.InterruptedException;
import java.util.Collections;

class Chatter extends AbstractActor {

    // number of chat messages to send
    final static int N_MESSAGES = 5;
    private Random rnd = new Random();
    private List<ActorRef> group; // the list of peers (the multicast group)
    private int sendCount = 0;    // number of sent messages
    private String myTopic;  // The topic I am interested in, null if no topic
    private final int id;    // ID of the current actor
    private int[] vc;        // the local vector clock

    // a buffer storing all received chat messages
    private StringBuffer chatHistory = new StringBuffer();
    // message queue to hold out-of-order messages
    private List<ChatMsg> mq = new ArrayList<>();


    /* -- Actor constructor --------------------------------------------------- */
    public Chatter(int id, String topic) {
        this.id = id;
        this.myTopic = topic;
    }

    static public Props props(int id, String topic) {
        return Props.create(Chatter.class, () -> new Chatter(id, topic));
    }



    /* -- Message types ------------------------------------------------------- */

    // Start message that informs every chat participant about its peers
    public static class JoinGroupMsg implements Serializable {
        private final List<ActorRef> group; // list of group members
        public JoinGroupMsg(List<ActorRef> group) {
            this.group = Collections.unmodifiableList(group);
        }
    }

    // A message requesting the peer to start a discussion on his topic
    public static class StartChatMsg implements Serializable {}

    // Chat message
    public static class ChatMsg implements Serializable {
        public final String topic;   // "topic" of the conversation
        public final int n;          // the number of the reply in the current topic
        public final int senderId;   // the ID of the message sender
        public final int[] vc;       // vector clock

        public ChatMsg(String topic, int n, int senderId, int[] vc) {
            this.topic = topic;
            this.n = n;
            this.senderId = senderId;
            this.vc = new int[vc.length];
            for (int i=0; i<vc.length; i++)
                this.vc[i] = vc[i];
        }
    }

    // A message requesting to print the chat history
    public static class PrintHistoryMsg implements Serializable {}



    /* -- Actor behaviour ----------------------------------------------------- */
    private void sendChatMsg(String topic, int n) {
        this.vc[id] ++;
        this.sendCount++;
        ChatMsg m = new ChatMsg(topic, n, this.id, this.vc);
        System.out.printf("%02d: %s%02d\n", this.id, topic, n);
        multicast(m);
        appendToHistory(m); // append the sent message
    }


    //We model random network delays with this code
    private void multicast(Serializable m) { // our multicast implementation
        List<ActorRef> shuffledGroup = new ArrayList<>(group);
        Collections.shuffle(shuffledGroup);
        for (ActorRef p: shuffledGroup) {
            if (!p.equals(getSelf())) { // not sending to self
                p.tell(m, getSelf());
                try { Thread.sleep(rnd.nextInt(10)); }
                catch (InterruptedException e) { e.printStackTrace(); }
            }
        }
    }

  /*private void multicast(Serializable m) { // our multicast implementation
    for (ActorRef p: group) {
    if (!p.equals(getSelf())) // not sending to self
    p.tell(m, getSelf());
    }
    }*/

    // Here we define the mapping between the received message types
    // and our actor methods
    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(JoinGroupMsg.class,    this::onJoinGroupMsg)
                .match(StartChatMsg.class,    this::onStartChatMsg)
                .match(ChatMsg.class,         this::onChatMsg)
                .match(PrintHistoryMsg.class, this::printHistory)
                .build();
    }




    private void onJoinGroupMsg(JoinGroupMsg msg) {
        this.group = msg.group;
        // create the vector clock
        this.vc = new int[this.group.size()];
        System.out.printf("%s: joining a group of %d peers with ID %02d\n",
                getSelf().path().name(), this.group.size(), this.id);
    }


    private void onStartChatMsg(StartChatMsg msg) {
        sendChatMsg(myTopic, 0); // start topic with message 0
    }

    private void onChatMsg(ChatMsg msg) {

        if (canDeliver(msg)){
            while (msg != null) {
                updateLocalClock(msg);
                deliver(msg);
                msg = findDeliverable();  // try to find other messages to deliver
            }
        }
        else {
            this.mq.add(msg);   // cannot deliver m right now, putting it on hold
            System.out.printf("%02d: enqueue from %02d %s local: %s queue length: %d\n",
                    this.id, msg.senderId,
                    Arrays.toString(msg.vc), Arrays.toString(this.vc),
                    mq.size());
        }
    }

    // find a message in the queue that can be delivered now
    // if found, remove it from the queue and return it
    private ChatMsg findDeliverable() {
        Iterator<ChatMsg> I = mq.iterator();
        while (I.hasNext()) {
            ChatMsg m = I.next();
            if (canDeliver(m)) {
                I.remove();
                return m;
            }
        }
        return null;        // nothing can be delivered right now
    }

    private boolean canDeliver(ChatMsg incoming) {
        if (incoming.vc[incoming.senderId] != vc[incoming.senderId] + 1)
            return false;

        for (int i=0; i<vc.length; i++) {
            if (i!=incoming.senderId && incoming.vc[i] > vc[i])
                return false;
        }
        return true;
    }

    private void updateLocalClock(ChatMsg m) {
        for (int i=0; i<vc.length; i++)
            vc[i] = (m.vc[i] > vc[i]) ? m.vc[i] : vc[i];
    }

    private void deliver(ChatMsg m) {
        // Our "chat application" appends all the received messages to the
        // chatHistory and replies if the topic of the message is interesting
        appendToHistory(m);

        if (myTopic != null && m.topic.equals(myTopic)  // the message is on my topic
                && sendCount < N_MESSAGES) // I still have something to say
        {
            final String topic;   // "topic" of the conversation
            final int n;          // the number of the reply in the current topic
            final int senderId;   // the ID of the message sender
            // reply to the received message with an incremented value and the same topic
            sendChatMsg(m.topic, m.n+1);
        }
    }

    private void appendToHistory(ChatMsg m) {
        this.chatHistory.append(m.topic + m.n + " ");
    }

    private void printHistory(PrintHistoryMsg msg) {
        System.out.printf("%02d: %s\n", this.id, this.chatHistory);
    }
}
