package rockTheJVM.akkaEssentials.part4faulttolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Kill, PoisonPill, Props, Terminated}

/**
 * @author George Mandilaras (NKUA)
 */
object StartingStoppingActors extends App{

    val system = ActorSystem("StartingStoppingActors")

    object Parent {
        case class StopChild(name: String)
        case class StartChild(name: String)
        case object Stop
    }
    class Parent extends Actor with ActorLogging {
        import Parent._
        override def receive: Receive = withChildren(Map())

        def withChildren(children: Map[String, ActorRef]): Receive ={
            case StartChild(name) =>
                log.info(s"Starting child with name: $name")
                val newChildren = children + (name -> context.actorOf(Props[Child], name))
                context.become(withChildren(newChildren))
            case StopChild(name) =>
                log.info(s"Stopping child with name: $name")
                val childOpt = children.get(name)
                /** Note: does not stop immediately - child might get some messages*/
                childOpt.foreach(child => context.stop(child))
            case Stop =>
                log.info("Parent received stop")
                /** Note: It will stop all children */
                context.stop(self)
            case message =>
                log.info(s"I received: $message")

        }
    }

    class Child extends Actor with ActorLogging {
        override def receive: Receive = {
            case message => log.info(s"I received: $message")
        }
    }

    /**
     * Method #1 - using context.stop(actorRef)
     * */
    import Parent._
    val parent = system.actorOf(Props[Parent], "parent")

    parent ! StartChild("child1")
    val child1 = system.actorSelection("/user/parent/child1")
    child1 ! "hi kid"
    parent ! StopChild("child1")
    for (i <- 1 to 50) child1 ! s"$i) Kid, Are you still there?"

    parent ! StartChild("child2")
    val child2 = system.actorSelection("/user/parent/child2")
    child2 ! "hi second kid"

    parent ! Stop
    // Note: this should not be received as it has already received the stopping signal
//    for (i <- 1 to 10) parent ! s"$i) Parent, Are you still there?"
//    for (i <- 1 to 100) child2 ! s"$i) Second Kid, Are you still alive?"

    Thread.sleep(1000)
    print("\n\n")

    /**
     * Method #2 - using special messages
     *  -PoisonPill
     *  -Kill
     */

    val looseActor = system.actorOf(Props[Child], "looseChild")
    looseActor ! "Hi loose child"
    // Note: Invoke context.stop
    looseActor ! PoisonPill
    looseActor ! "Are you still there?"

    val looseActor2 = system.actorOf(Props[Child], "looseChild2")
    looseActor2 ! "Hi loose child2"

    // Note: Throws AkkaKillExceptions - kill Actor Brutally
    looseActor ! Kill
    looseActor ! "Are you still there?"

    Thread.sleep(1000)
    print("\n\n")

    /**
     * Death Watch
     */

    class DeathWatcher extends Actor with ActorLogging {
        import Parent._
        override def receive: Receive = {
            case StartChild(name) =>
                log.info(s"Starting child with name: $name")
                val child = context.actorOf(Props[Child], name)
                log.info(s"Started child with name: $name")
                //Note: due to context.watch, self will receive the special message Terminated
                context.watch(child)
            case Terminated(ref) =>
                log.info(s"The reference that I am watching $ref has been stopped")
        }
    }

    val parentWatcher = system.actorOf(Props[DeathWatcher], "parentWatcher")
    parentWatcher ! StartChild("watchedChild")
    val watchedChild = system.actorSelection("/user/parentWatcher/watchedChild")
    Thread.sleep(500)
    watchedChild ! PoisonPill
}
