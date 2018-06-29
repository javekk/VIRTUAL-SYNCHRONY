## Flush Protocol

#### While a node n wants to join
 * [n] Sends a JoinRequest message to the coordinator
 * [Coordinator] Goes unstable (stop sending messages)
 * [Coordinator] Assigns an id to the node, and send the NewId message to n
 * [Coordinator] Updates its view adding n ( but the incoming messages with old view)
 * [Coordinator] Multicasts the new view
 * [Coordinator] Multicasts the Flush within the new view 
 * [n] Receives a new view
 * [n] Goes unstable 
 * [n] Updates its view adding n ( but the incoming messages with old view)
 * [n] Multicasts unstable within the new view 
 * [n] Multicasts Flush within the new view 
 * [Coordinator] [n] When it receives the flush from each node in the group returns stable and install the new view
 

##While some nodes n crash
 * [Coordinator] Detects the crashed nodes
 * [Coordinator] Updates its view removing crashed nodes ( but the incoming messages with old view)
 * [Coordinator] Multicasts the new view
 * [Coordinator] Multicasts flush messages with the crashed nodes last messages
 * [n] When receives a newView Message, gets crashed nodes last messsages 
 * [n] Multicasts unstable messages with the new view
 * [n] Multicasts flush with new view and crashed nodes last messsages 
 * [Coordinator] [n] When receives a Flush Message, check if the messages they have are the last messages sent from the crashes node -> if not the update and deliver both the messages of each crashed node (each message has its own sequence number)
 * [Coordinator] [n]  When it receives the flush from each node in the group returns stable and install the new view
   
   
 It may be possible to receive a flush message with the new view before the view message itself, in this case, our solution to simplify the implementation is to update the view usign the one in the flush message.

 
 
 