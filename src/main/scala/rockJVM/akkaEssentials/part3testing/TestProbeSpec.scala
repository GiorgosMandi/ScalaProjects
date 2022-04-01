package rockJVM.akkaEssentials.part3testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

class TestProbeSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender
  with AnyWordSpecLike
  with BeforeAndAfterAll{

    import TestProbeSpec._

    override def afterAll(): Unit={
        TestKit.shutdownActorSystem(system)
    }

    "a master actor" should  {

        "register a slave" in {
            val master = system.actorOf(Props[Master])
            val slave = TestProbe("slave")
            master ! Register(slave.ref)
            expectMsg(RegistrationAck)
        }

        "send work to slave Actor" in {
            val master = system.actorOf(Props[Master])
            val slave = TestProbe("slave")
            master ! Register(slave.ref)
            expectMsg(RegistrationAck)

            val text = "I love Akka"
            master ! Work(text)
            slave.expectMsg(SlaveWork(text, self))
            slave.reply(WorkCompleted(3, self))
            expectMsg(Report(3))
        }

        "aggregate results correctly" in {
            val master = system.actorOf(Props[Master])
            val slave = TestProbe("slave")
            master ! Register(slave.ref)
            expectMsg(RegistrationAck)

            val text = "I love Akka"
            master ! Work(text)
            master ! Work(text)

            slave.receiveWhile(){
                case SlaveWork(`text`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
            }
            expectMsg(Report(3))
            expectMsg(Report(6))

        }
    }
}

object TestProbeSpec {

    case class Work(text: String)
    case class SlaveWork(text: String, parent: ActorRef)
    case class WorkCompleted(count: Int, parent: ActorRef)
    case class Register(slave: ActorRef)
    case class Report(totalCount: Int)

    case object RegistrationAck



    /**Note:
     *  this master interacts with multiple things in the world so it's hard to test
     *  general assertions are not sufficient here
     *
     */
    class Master extends Actor {
        override def receive: Receive = {
            case Register(slaveRef) =>
                context.become(online(slaveRef, 0))
                sender() ! RegistrationAck
        }

        def online(slaveRef: ActorRef, totalCount: Int): Receive = {
            case Work(text) => slaveRef ! SlaveWork(text, sender())
            case WorkCompleted(count, initialSender) =>
                val newTotal = totalCount + count
                initialSender ! Report(newTotal)
                context.become(online(slaveRef, newTotal))
        }
    }

    class Slave extends Actor {
        override def receive: Receive = {
            case SlaveWork(text, initialSender) =>
                sender() ! WorkCompleted(text.split("\\s").length, initialSender)
        }
    }
}
