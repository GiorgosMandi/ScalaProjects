package rockTheJVM.akkaEssentials.part4faulttolerance

import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{Actor, ActorRef, ActorSystem, AllForOneStrategy, OneForOneStrategy, Props, SupervisorStrategy, Terminated}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import rockTheJVM.akkaEssentials.part4faulttolerance.SupervisionSpec.AllForOneSupervisor

import java.util.EventListener

/**
 * @author George Mandilaras (NKUA)
 */
class SupervisionSpec extends TestKit(ActorSystem("BasicSpec"))
    with ImplicitSender
    with AnyWordSpecLike
    with BeforeAndAfterAll {

    import SupervisionSpec._

    override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

    "A supervisor" should {
        val supervisor = system.actorOf(Props[Supervisor], "supervisor")
        "resume child in case of a minor fault" in {
            supervisor ! Props[FuzzyWordCounter]
            val child = expectMsgType[ActorRef]

            child ! "I love Akka"
            child ! Report
            expectMsg(3)

            // will fail, resume and maintain the same state
            child ! "Akka is awesome because I am learning to think in a whole new way"
            child ! Report
            expectMsg(3)
        }

        "restart child in case of an empty sentence" in {
            supervisor ! Props[FuzzyWordCounter]
            val child = expectMsgType[ActorRef]
            child ! "I love Akka"
            child ! Report
            expectMsg(3)
            // Note: Restart nad destroy internal state
            child ! ""
            child ! Report
            expectMsg(0)
        }

        "terminate itself in case of a major issue" in {
            supervisor ! Props[FuzzyWordCounter]
            val child = expectMsgType[ActorRef]
            watch(child)

            // Note: child Will stop, this will invoke a Terminated caught by self
            child ! "akka is nice"
            val terminatedMsg = expectMsgType[Terminated]
            assert(terminatedMsg.actor == child)
        }

        "escalate error when you dont know what to do" in {
            supervisor ! Props[FuzzyWordCounter]
            val child = expectMsgType[ActorRef]
            watch(child)

            // Note: the exception will be forwarded to the parent
            // hence the parent will restart and kill all of its children
            child ! 42
            val terminatedMsg = expectMsgType[Terminated]
            assert(terminatedMsg.actor == child)
        }
    }
    "A kinder guardian" should {
        "not restart children in case it's restarted or escalate failures" in {
            val supervisor = system.actorOf(Props[NoDeathOnRestartSupervisor], "kinderSupervisor")
            supervisor ! Props[FuzzyWordCounter]
            val child = expectMsgType[ActorRef]
            child ! "I love Akka"
            child ! Report
            expectMsg(3)

            child ! 42
            child ! Report
            // parent does not kill it but restarts it
            expectMsg(0)

        }
    }

    "all-for-one supervisor" should {
        "apply all-for-one strategy" in {
            val supervisor = system.actorOf(Props[AllForOneSupervisor], "AllForOneSupervisor")
            supervisor ! Props[FuzzyWordCounter]
            val child = expectMsgType[ActorRef]

            supervisor ! Props[FuzzyWordCounter]
            val secondChild = expectMsgType[ActorRef]
            secondChild ! "I love Akka"
            secondChild ! Report
            expectMsg(3)

            EventFilter[NullPointerException]() intercept {
                child ! ""
            }
            // wait to ensure that the strategy has been applied
            Thread.sleep(500)
            // has restarted due to all-for-one, so the state will have been re-initialized
            secondChild ! Report
            expectMsg(0)


        }
    }
}

object SupervisionSpec {
    case object Report

    class Supervisor extends Actor{

        // apply these decisions on the exact actor that caused the failure
        override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy(){
            case _: NullPointerException => Restart
            case _: IllegalArgumentException => Stop
            case _: RuntimeException => Resume
            case _: Exception => Escalate
        }
        override def receive: Receive = {
            case props: Props =>
                val childRef = context.actorOf(props)
                sender() ! childRef
        }
    }

    class NoDeathOnRestartSupervisor extends Supervisor{
        override def preRestart(reason: Throwable, message: Option[Any]): Unit = { /* do nothing - override regular procedure*/}
    }
 
    class AllForOneSupervisor extends Supervisor {
        // apply these decisions to ALL the actors
        override val supervisorStrategy: AllForOneStrategy = AllForOneStrategy(){
            case _: NullPointerException => Restart
            case _: IllegalArgumentException => Stop
            case _: RuntimeException => Resume
            case _: Exception => Escalate
        }
    }

    class FuzzyWordCounter extends Actor {
        var words: Int = 0

        override def receive: Receive = {
            case Report => sender() ! words
            case "" => throw new NullPointerException("Sentence is empty")
            case sentence: String =>
                if (sentence.length > 20) throw new RuntimeException("Sentence is too big")
                else if (!Character.isUpperCase(sentence(0))) throw new IllegalArgumentException("Sentence must start with upper case")
                else words += sentence.split("\\s").length
            case _ => throw new Exception("Can only receive Strings")
        }
    }
}

