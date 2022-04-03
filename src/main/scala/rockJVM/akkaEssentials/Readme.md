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

