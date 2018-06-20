# VIRTUAL-SYNCHRONY

In this project, it is implemented a simple peer-to-peer group communication service providing
the virtual synchrony guarantees.

The project is implemented in Akka with the group members being Akka actors that send multicast
messages to each other.  The system allows adding new participants to the group as well as tolerate silent
crashes of some existing participants at any time.

## Reliable multicast
* All message sent in Vi are delivered by all operational nodes or by none if sender crashes while multicasting.
* Multicast (random unicast, with random delays)

## Group manager
* Reliable actor (ID:0), called "group manager".
* Keeps track of views, sends views updates (when new node joins or when a node crashes)
* Can receive crash notifications from other actors, if receives crash notification -> send view update without crashed node
* Assigns ID to other actors
* When receives request for joining -> send view update with new node

## Joining 			
* Actor joining sends a request to group manager (actor with ID=0)
* Receives new View with him included
* Starts sending multicast
* If using network -> run new instance, specifying IP address and port of group manager

## Data traffic
* All actors must multicast continuously, with random intervals (below predefined T)
* System (receiving actors) must detect duplicated messages -> deliver at*most*once

## Stable messages
* An actor can only send a new multicast message when it has successfully send previuos one to all nodes
* If an actor receive a message from P it means that previous messages from P are stables

## Crash detection
* Option 1: if node (maybe only group manager) does not receive message from a specific node after a timeout -> that node crashed
* Option 2: specific messages sent by all to group manager to notify that they are up (sent periodically i.e every 3 secs)
* Option 3: a node timeout expecting FLUSH message form another node -> notify group manager that this node crashed

## Network
* Each node is a separate process comunicating through the network

## Emulate crash
* If using network -> shut down process

## Join/crash position
* During sending multicast
* After receiving multicast
* After receiving view change

## Log file
* Each operation write in log :

```															
	- <ID> install view <view seqnum> <participant list> — with comma*separated IDs
```												
```
	– <ID> send multicast <seqnum> within <view seqnum>
```
```
	– <ID> deliver multicast <seqnum> from <ID> within <view seqnum>  
```




