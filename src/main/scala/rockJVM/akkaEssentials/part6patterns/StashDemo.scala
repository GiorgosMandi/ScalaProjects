package rockJVM.akkaEssentials.part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App{
    /*
    ResourceActor
     - open => it can receive read/write requests to the resources
     - otherwise => postpone all read/write requests until the state is open

     ResourceActor is closed
     - Open => switch to the open state
     - Read | Write => POSTPONE

     ResourceActor is open
     - Read | Write => handle
     - Close => switch to closed state

     *Use cases*
     Mailbox: [Open, Read, Read, Write]
     - switch to open
     - read
     - read
     - write

     Mailbox: [Read, Open, Read]
     - Read => stash Read
        stash: [Read]
     - Open => switch to the open state, unstash -> Mailbox: [Read, Read]
     */

    case object Open
    case object Close
    case object Read
    case class Write(data:String)

    // step 1 - extend stash trait
    class ResourceActor extends Actor with ActorLogging with Stash {
        private var innerData: String = ""

        override def receive: Receive = closed

        def closed: Receive = {
            case Open =>
                log.info("Opening resource")
                // step 3 - unstash all when you switch the message handler
                unstashAll()
                context.become(open)
            case message =>
                log.info(s"Stashing $message because I can't handle it in the closed state")
                // step 2 - stash away what you cant handle
                stash()
        }

        def open: Receive = {
            case Read =>
                log.info(s"Reading resource: $innerData")
            case Write(content) =>
                log.info(s"I am writing: $content")
                innerData += content + "|"
            case Close =>
                log.info("Closing resource")
                unstashAll()
                context.become(closed)
            case message =>
                log.info(s"Stashing $message because I can't handle it in the open state")
                stash()
        }
    }

    val system = ActorSystem("StashingDemo")
    val resourceActor = system.actorOf(Props[ResourceActor], "resourceActor")
    resourceActor ! Write("I love stash") // stashed
    resourceActor ! Open    // Open -> unstash -> Write
    resourceActor ! Read    // Read
    resourceActor ! Open    // stashed
    resourceActor ! Close   // Close -> unstash -> Open
    resourceActor ! Write("I also love Akka") // Writ
    resourceActor ! Read    // Read
    resourceActor ! Close   // Close

}
