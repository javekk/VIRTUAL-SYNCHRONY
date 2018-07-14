# VIRTUAL-SYNCHRONY

*Authors*:
- Raffaele Perini
- Giovani Rafael Vuolo

In this project, it is implemented a simple peer-to-peer group communication service providing the **virtual synchrony** guarantees.

The project is implemented in Akka with the group members being Akka actors that send multicast messages to each other.  The system allows adding new participants to the group as well as tolerate silent crashes of some existing participants at any time.

## System Properties

#### Reliable multicast
* All messages sent in view i are delivered by all operational nodes or by none if sender crashes while multicasting.

#### Coordinator
* Reliable actor (ID:0), called "group manager" or "coordinator".
* Keeps track of views, **sends views updates** (when new node joins or when a node crashes).
* Assigns ID to other actors, based on their names

#### Joining 			
* Actor joining sends a request to group manager (actor with ID=0)
* Receives new View with him included
* Starts sending multicast

#### Data traffic
* All actors  multicast continuously, with random intervals  (between 1 and 6 seconds).
* System (receiving actors) detects duplicated messages: deliver **at-most-once**.

#### Stable messages
* An actor can only send a new multicast message when it has successfully send previuos one to all nodes.
* If an actor receive a message from P it means that previous messages from P are stables.

#### Crash detection
* All actors send heartbeat to coordinator every 4.5 seconds.
* Coordinator checks if it has received the heartbeats from all actors, if not, it will start a new view.

#### Log file
* Each operation write in log :

```
 - <ID> install view <view seqnum> <participant list> — with comma*separated IDs
 - <ID> send multicast <seqnum> within <view seqnum>
 - <ID> deliver multicast <seqnum> from <ID> within <view seqnum>  
```

## Flush implementation

Here logic of the flush protocol implemented, notice that it is possible to receive view i+k, before i+1 is installed

##### On receive View i+k

```
	inihibit_sends++; // until more than zero, no messages are sent
	multicast(all_unstable_messages, view j); // in the view j < i+k
	delvier(all_unstable_messages);
	multicast(flush(view i+k));
```

##### On receive unstable message

```
	if( unstableMsg.viewId < this.viewId){
		\\duplicate, ignore it
	}
	else if( unstableMsg.viewId == this.viewId){
		if(unstableMsg.isNotADuplicate()){
			deliver(unstableMsg);
		}
	} 
	else if (unstableMsg.viewId > this.viewID){
		messageBuffer.save(unstableMsg); // it will deliver, when its view will be installed
	}
```

##### on receive Flush i+k

```
	if(received_all_flushes_for_view(i+k)) { //not from the crashed nodes
		install_view(view(i+k));
		inihibit_sends--; // if zero, node can return to multicast
	}
```


## Execution

Run the following in the root to start the program:
```
    sudo gradle run
```
In the *App* class, that correspond to the *Execution_3* class in the Test branch is scheduled the following behaviuor:

 *  Node0 is the coordinator.
 *  Node1 and Node2 join (*view1, view2*).
 *  Node3 joins after 10 seconds (view3).
 *  Node2 crashes when receives the view 3.  (*view4*) [#**crash after receiving view change**] 
 *  Node4 join while the system is waiting for node2 (*view5*)
 *  *view3, view4,view5 installed all together.
 *  Node3 crashes while is multicasting. (*view6*) [#**crash during sending multicast**] 
 *  Node4 crashes after 10 seconds. (*view7*) [#**crash after receiving multicast**]
 *  Node4 recover after 25 seconds. (*view8*).
