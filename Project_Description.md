## reliable multicast
* all message sent in Vi are delivered by all operational nodes or by none if sender crashes while multicasting.
* multicast (random unicast, with random delays)

## group manager
* reliable actor (ID:0), called "group manager".
* keeps track of views, sends views updates (when new node joins or when a node crashes)
* can receive crash notifications from other actors, if receives crash notification -> send view update without crashed node
* assigns ID to other actors
* when receives request for joining -> send view update with new node

## joining 			
* actor joining sends a request to group manager (actor with ID=0)
* receives new View with him included
* starts sending multicast
* if using network -> run new instance, specifying IP address and port of group manager

## data traffic
* all actors must multicast continuously, with random intervals (below predefined T)
* system (receiving actors) must detect duplicated messages -> deliver at*most*once

## stable messages
* an actor can only send a new multicast message when it has successfully send previuos one to all nodes
* if an actor receive a message from P it means that previous messages from P are stables

## crash detection
* option 1: if node (maybe only group manager) does not receive message from a specific node after a timeout -> that node crashed
* option 2: specific messages sent by all to group manager to notify that they are up (sent periodically i.e every 3 secs)
* option 3: a node timeout expecting FLUSH message form another node -> notify group manager that this node crashed

## network
* each node is a separate process comunicating through the network

## log file
* each operation write in log :
															- <ID> install view <view seqnum> <participant list> — with comma*separated IDs
															– <ID> send multicast <seqnum> within <view seqnum>
															– <ID> deliver multicast <seqnum> from <ID> within <view seqnum>  

## emulate crash
* enter in "crash mode" -> ignore incoming messages and stop sending any kind of message
* if using network -> shut down process

## join/crash position
* during sending multicast
* after receiving multicast
* after receiving view change
