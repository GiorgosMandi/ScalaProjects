package rockJVM.akkaEssentials.part3testing

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TimedAssertionsSpec extends TestKit(ActorSystem("BasicSpec"))
  with ImplicitSender
  with AnyWordSpecLike
  with BeforeAndAfterAll {

    import TimedAssertionsSpec._

    override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

    "A Worker " should {
        "ends within a minute" in {
            val worker = system.actorOf(Props[WorkerActor])
            within(200 millis, 700 millis) {
                worker ! Work
                expectMsg(WorkerResult(1))
            }
        }
        "reply with valid work at reasonable cadence" in {
            val worker = system.actorOf(Props[WorkerActor])
            worker ! WorkSequence
            within(200 millis, 1000 millis) {
                // receiveWhile can be configured in various ways so to be adjusted to the desired behaviour of the Actor
                val results: Seq[Int] = receiveWhile(max = 2 seconds, idle = 500 millis, messages = 10) {
                    case WorkerResult(result) => result
                }
                assert(results.sum == 10)
            }
        }
    }
}

object TimedAssertionsSpec {

    case class WorkerResult(v: Int)

    case object Work

    case object WorkSequence

    class WorkerActor extends Actor {
        override def receive: Receive = {
            case Work =>
                Thread.sleep(500)
                sender() ! WorkerResult(1)
            case WorkSequence =>
                val r = new Random()
                for (i <- 1 to 10) {
                    Thread.sleep(r.nextInt(50))
                    sender() ! WorkerResult(1)
                }
        }
    }

}