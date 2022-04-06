# Rock The JVM: Akka Essentials

Akka is a multithreading framework for the JVM that enables us to build powerful reactive, concurrent, and distributed applications more easily. 
Akka is based on Actors, which are objects which we can only interact with via messages. Actors have the following properties:
- every interaction happens via messaging
- messages are asynchronous
- we can't directly access the Actor object


## Actors Basic
An Actor implementation, looks like this 

```scala
import akka.actor.Actor

class SimpleActor extends Actor {
     override def receive: Receive = {
         case number: Int => println(s"[$self]: I have received a number: $number")
         case Some(content) => println(s"[$self]: I have received a special message: $content")
         case "self" => println(s"[$self]: My self is ${self}")
         case message: String => println(s"[$self]: I have received from ${sender().path} message: $message")
     }
 }
```

`Receive` is a type definition for `PartialFunction[Any, Unit]`. To initialize and communicate with an Actor
do something like:

Note, that we never directly access actors, so to communicate with an actor, we create an `ActorRef` and we 
interact with this. We never initialize and access the fields of an actor.

```scala
import akka.actor.{ActorRef, ActorSystem, Props}

val system = ActorSystem("actorCapabilitiesDemo")
val simpleActor: ActorRef = system.actorOf(Props[SimpleActor], "simpleActor")
simpleActor ! "hello, Actor"
simpleActor ! 42
simpleActor ! Some("this is special")
```
The messages can be of Any type **BUT** they must be:
- IMMUTABLE
- SERIALIZABLE

*Usually we communicate with Actors via case classes/objects*

### Actors Hierarchy
Within Actors there is a hierarchy, an Actor can create other actors which can manage.
Thus, we can see that there is a hierarchy between actors:
```
parent -> daughter -> grandSon
        \
         -> son -> grandDaughter
```
This representation is displayed in Actors' `path`.
```
akka://system/user/parent
akka://system/user/parent/daughter
akka://system/user/parent/son
akka://system/user/parent/daughter/grandSon
akka://system/user/parent/son/grandDaughter
```
Notice that the parent actor is not a root actor, but it originates from `akka://system/user/`
Both `system` and `user` is a top level Actors known as **Guardian-Actors**. The Guardian 
Actors are:
- `/system`: system guardian
- `/user`: user-level guardian
- `/`: root guardian

Using this `path` we can pick a specific actor using `system.actorSelection`
```scala
val childSelection = system.actorSelection("/user/parent/child")
childSelection ! "I found you"
```

### Stopping an Actor
To stop an Actor, we can send him one of two special messages: 
- PoisonPill
- Kill

`PoisonPill` will smoothly stop an Actor by calling `context.stop`, while `Kill` will brutally kill him by
throwing a `AkkaKillExceptions`.
```scala
import akka.actor.{Kill, PoisonPill}
simpleActor1 ! Kill
simpleActor2 ! PoisonPill
```
Any Actor can watch another Actor using `context.watch(ref)`. This way, if the watched actor dies, the watcher will
receive the special message `Terminated(ref)` 

## Actors Life-Cycle

Actors can be 
 - started: create a new ActorRef with a UUID at a given path
 - suspended: the ActorRef will enqueue but **NOT** process any new messages
 - resumed: the ActorRef will continue process the enqueued messages
 - restarted: restarts an actor by destroying its internal state (this can happen by an exception). This occurs by doing the following steps:
   - suspend
   - swap actor instance (preRestart, replace with new instance, and finally calls postRestart)
   - resume
   
 - stop: stops an ActorRef. All watchers of the ref will receive `Terminated(ref)` message

*Note, that when a parent is being restarted, all of its children get stopped!*

Classic actors have methods `preStart`, `preRestart`, `postRestart` and `postStop` that can be overridden to act on changes to 
the actor’s lifecycle.


## Supervision Strategy

Akka prides itself about fault tolerance! Parent actors decide how to handle a child failure. 

When an actor fails:
- suspends all of its children 
- sends a special message to its parent

The parent can decide to:
- resume the actor
- fail and escalate the failure to its parent

We can do this by overriding the `supervisorStrategy`. This way we can define the behaviour of the 
spawning children and ensure fault-tolerance and self-healing.

```scala
override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy(){
    case _: NullPointerException => akka.actor.SupervisorStrategy.Restart
    case _: IllegalArgumentException => akka.actor.SupervisorStrategy.Stop
    case _: RuntimeException => akka.actor.SupervisorStrategy.Resume
    case _: Exception => akka.actor.SupervisorStrategy.Escalate
}
```

## Scheduler & Timers

Akka enables us to schedule tasks to run once or periodically, after a predefined period of time. 
We do this using `system.scheduler.scheduleOnce` or `system.scheduler.schedule`, which both returns a 
`Cancelable` enabling us to cancel the task if we want. See example:

```scala
 // schedule job to run once after 1 second
 system.scheduler.scheduleOnce(1 second){
     simpleActor ! "Start"
 }(system.dispatcher)

 // schedule job to run repeatedly every 2 seconds, after 1 second
 val routine = system.scheduler.schedule(1 second, 2 second){
     simpleActor ! "HeartBeat"
 }(system.dispatcher)

 // cancel repeated scheduler
 system.scheduler.scheduleOnce(5 second){
     routine.cancel()
 }(system.dispatcher)
```
We can also schedule jobs to run within an Actor, by implementing a `Timers`. With `Timers`, we can schedule 
jobs to run once (by sending messages), to run repeatedly and to cancel them, very similar as `system.schedule`
See example:

```scala
case object TimerKey
case object Start
case object Stop
case object Reminder
case object Pause
class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
  // send message to myself after 500ms
  timers.startSingleTimer(TimerKey, Start, 500 millis)

  override def receive: Receive = {
      case Start =>
          log.info("Bootstrapping")
          // send repeatedly message Reminder to myself every 1s
          timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
      case Reminder =>
          log.info("I am alive")
      case Pause =>
          log.info("I am stopping timer")
          // Stop specific Timer
          timers.cancel(TimerKey)
      case Stop =>
          log.info("I am stopping")
          context.stop(self)
  }
}
val timerActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")

system.scheduler.scheduleOnce(6 second) {
  timerActor ! Stop
}(system.dispatcher)

system.scheduler.scheduleOnce(3 second) {
  timerActor ! Pause
}(system.dispatcher)
```

## Routers

Routers are extremely useful when you want to delegate or spread work between multiple
actors of the same kind. Routers are usually meta-level actors that forward messages to other actors,
either created by router itself or from the outside. 

Supported options for routing logic:

- *RoundRobin*: cycles between routines
- *Random*: randomly pick the next actor (not recommended)
- *Smallest Mailbox*: send to the actor with the fewer messages in the queue
- *Broadcast*: send the same message to all actors
- *Scatter-Gather first*: broadcast message to everyone and delegate to the first who replies - all others replies are discarded
- *Tail-Chopping*: send message sequentially to actors and delegate to the first who replies - all others replies are discarded
- *Consistent Hashing*: all the messages with the same hash go to the same actor

There are three main ways of defining routers:

1. Manual: by adding/removing slaves in a `Router` (not recommended)
2. Pool Routers: Router can create child actors in itself, by calling a logic pool (i.e., RoundRobingPool)
   
```scala
   val masterPool = system.actorOf(RoundRobinPool(nrOfInstances = 5).props(Props[Slave]))
```

3. Group Routers: assign routees that are defined elsewhere, by only using their `path`
```scala
val slavesPaths = slavesList.map(slaveRef => slaveRef.path.toString)
val groupMaster = system3.actorOf(RoundRobinGroup(slavesPaths).props())
```

## Mailbox

Akka allows us to overwrite the behaviour of mailbox, for instance we can prioritize some messages
This can be done as following

```scala
 /**
  * Interesting case #1  - custom priority mailbox
  * P0 -> most important
  * P1 -> less important
  * P2 -> less important
  * P3 -> least important
  */

 // Step 1 - make mailbox configuration
 class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
   extends UnboundedPriorityMailbox(PriorityGenerator{
       case message: String if message.startsWith("[P0]") => 0
       case message: String if message.startsWith("[P1]") => 1
       case message: String if message.startsWith("[P2]") => 2
       case message: String if message.startsWith("[P3]") => 3
       case _ => 4
   })

 // Step 2 - make it known to the configuration
 // Step 3 - attach the dispatcher to an actor
 val supportTickerLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"), "supportTickerLogger")
 supportTickerLogger ! PoisonPill // even this is postponed
 supportTickerLogger ! "[P3] this would be nice to have"
 supportTickerLogger ! "[P0] this needs to be solved NOW!"
 supportTickerLogger ! "[P1] do this when you have the time"

```

## Stashing

Stashing allows us to postpone messages so to be handled in the future, 
for instance in a context state that supports them.
Stash works as a different queue that coexists with the mailbox, 
in which we store all the messages we want to process in the future. When we release the messages, they
are inserted in the start of the mailbox.
To use stash, the Actor must extend `akka.actor.Stash`. Then: 
- To stash message we `stash()`
- To release all messages and prepend them to the mailbox use `unstashAll()`

When we use Stash, we need be very careful with 
- potential memory bounds of Stash
- potential memory bounds of mailbox when we unstash
- no stashing twice -> throws Exception
- Stash trait overrides `preRestart`, so always extend it after `Actor` 