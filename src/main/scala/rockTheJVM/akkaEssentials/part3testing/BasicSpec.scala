package rockTheJVM.akkaEssentials.part3testing

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random


class BasicSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender
  with AnyWordSpecLike
  with BeforeAndAfterAll{
    import rockTheJVM.akkaEssentials.part3testing.BasicSpec._

    override def afterAll(): Unit={
        TestKit.shutdownActorSystem(system)
    }

    "A simple Actor" should {
        "send back the message" in {
            val actor = system.actorOf(Props[SimpleActor])
            val msg: String = "hello there"
            actor ! msg

            // we are the `testActor` imported from ImplicitSender
            expectMsg(msg)
        }
    }
    "A black hole Actor" should {
        "not return anything" in {
            val actor = system.actorOf(Props[BlackHoleActor])
            val msg: String = "hello there"
            actor ! msg

            expectNoMessage(1 second)
        }
    }

    "A lab test actor" should {
        val labTestActor = system.actorOf(Props[LabTestActor])
        "turn a string into uppercase" in {
            labTestActor ! "I love scala"
            // get reply
            val reply = expectMsgType[String]
            assert(reply == "I LOVE SCALA")
        }

        "reply to a greeting" in {
            labTestActor ! "greeting"
            expectMsgAnyOf("hi", "hello")
        }

        "reply to a favourite techs" in {
            labTestActor ! "favouriteTech"
            expectMsgAllOf("scala", "akka")
        }

        "reply with cool tech in a cooler way" in {
            labTestActor ! "favouriteTech"
            val messages: Seq[Any] = receiveN(2)
            assert(messages.head == "scala")
        }

        "reply with cool tech in a fancy way" in {
            labTestActor ! "favouriteTech"
            expectMsgPF(){
                case "scala" => // only care tha case is defined
                case "akka" =>
            }
        }
    }
}

object BasicSpec{

    class SimpleActor extends Actor{
        override def receive: Receive = {
            case msg => sender() ! msg
        }
    }

    class BlackHoleActor extends Actor with ActorLogging{
        override def receive: Receive = {
            case msg => log.info(s"I received ${msg.toString}")
        }
    }
    class LabTestActor extends Actor{
        val random = new Random()
        override def receive: Receive = {
            case "greeting" =>
                if (random.nextBoolean()) sender() ! "hi" else sender() ! "hello"
            case "favouriteTech" =>
                sender() ! "scala"
                sender() ! "akka"
            case msg: String =>
                sender() ! msg.toUpperCase()
        }
    }
}
