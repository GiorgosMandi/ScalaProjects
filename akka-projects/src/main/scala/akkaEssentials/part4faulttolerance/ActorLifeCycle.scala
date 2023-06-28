package rockTheJVM.akkaEssentials.part4faulttolerance

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}

/**
 * @author George Mandilaras (NKUA)
 */
object ActorLifeCycle extends App {

    /**
     * Start
     * Stop
     */
    case object StartChild
    class ActorLifeCycle extends Actor with ActorLogging{
        // Note: by default is empty
        override def preStart(): Unit = log.info("I am starting")
        // Note: by default is empty
        override def postStop(): Unit = log.info("I am stopping")

        override def receive: Receive = {
            case StartChild => context.actorOf(Props[ActorLifeCycle], "child")
        }
    }

    val system: ActorSystem = ActorSystem("LifeCycleDemo")
    val lifeCycleActor: ActorRef = system.actorOf(Props[ActorLifeCycle], "lifeCycleActor")
    lifeCycleActor ! StartChild

    // first stops child and then the parent
    lifeCycleActor ! PoisonPill


    Thread.sleep(1000)
    print("\n\n")

    /**
     * Restart
     */
    case object Fail
    case object FailedChild
    case object Check
    case object CheckChild
    class Parent extends Actor with ActorLogging{
        private val child = context.actorOf(Props[Child], "supervisedChild")

        override def receive: Receive = {
            case FailedChild => child ! Fail
            case CheckChild => child ! Check
        }
    }

    class Child extends Actor with ActorLogging {
        override def preStart(): Unit = log.info("Supervised Child Started")
        override def postStop(): Unit = log.info("Supervised Child Stopped")

        override def preRestart(reason: Throwable, message: Option[Any]): Unit =
            log.info(s"Supervised Child restarting because of ${reason.getMessage}")

        override def postRestart(reason: Throwable): Unit =
            log.info(s"Supervised child restarted")

        override def receive: Receive = {
            case Fail =>
                log.info("Child will fail now")
                // Note: Child restarts after an exception
                throw new RuntimeException("I have failed")
            case Check =>
                log.info("I am alive, yet")
        }
    }

    val supervisor: ActorRef = system.actorOf(Props[Parent], "supervisor")
    supervisor ! CheckChild
    supervisor ! FailedChild
    supervisor ! CheckChild
}
