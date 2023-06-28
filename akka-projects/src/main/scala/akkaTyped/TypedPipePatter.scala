package rockTheJVM.akkaTyped

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TypedPipePatter {

    /**
     * Handle an asynchronous call inside an Actor
     *
     *  v1 - handle is as a Future using onComplete
     *          - Future will run in another thread (race-condition)
     *          - stateful
     *  v2 -   PipePattern: forward the result of a future back to me as a message
     *         - thread-safe
     *         - stateful
     *  v3 - Behaviors.receive & PipePattern
     *          - thread-safe
     *         - stateless
     *         - fully functional
     */

    object Infrastructure {
        private implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

        private val db: Map[String, Int] = Map(
            "Daniel" -> 123,
            "Alice" -> 456,
            "Bob" -> 999
        )
        def asyncRetrievePhoneNumber(name: String): Future[Int] = Future(db(name))
    }

    trait PhoneCallProtocol
    case class FindAndCallPhoneNumber(name: String) extends PhoneCallProtocol
    case class InitiatePhoneCall(number: Int) extends PhoneCallProtocol
    case class LogPhoneCallFailure(reason: Throwable) extends PhoneCallProtocol

    val phoneCallInitiatorV1: Behavior[PhoneCallProtocol] = Behaviors.setup{ context =>
        implicit val ec: ExecutionContext = context.system.executionContext
        var nPhoneCalls = 0
        var nFailures = 0
        Behaviors.receiveMessage {
            case FindAndCallPhoneNumber(name) =>
                val futureNumber = Infrastructure.asyncRetrievePhoneNumber(name)
                futureNumber onComplete { // warning: happens on another thread
                    case Success(number) =>
                        // perform call
                        println(s"Initializing phone call for number $number")
                        nPhoneCalls += 1 // race conditioned - broke actor encapsulation
                    case Failure(exception) =>
                        println(s"Failed to get the for $name")
                        nFailures += 1 // race conditioned - broke actor encapsulation
                }
                Behaviors.same
        }
    }


    // PipePattern: The ability to forward the result of a future back to me as a message
    val phoneCallInitiatorV2: Behavior[PhoneCallProtocol] = Behaviors.setup { context =>
        implicit val ec: ExecutionContext = context.system.executionContext
        var nPhoneCalls = 0
        var nFailures = 0
        Behaviors.receiveMessage {
            case FindAndCallPhoneNumber(name) =>
                val futureNumber = Infrastructure.asyncRetrievePhoneNumber(name)
                context.pipeToSelf(futureNumber){ // note: thread safe
                    case Success(number) => InitiatePhoneCall(number)
                    case Failure(reason) => LogPhoneCallFailure(reason)
                }
                Behaviors.same
            case InitiatePhoneCall(number) =>
                println(s"Initializing phone call for number $number")
                nPhoneCalls += 1 //note: not a race
                Behaviors.same

            case LogPhoneCallFailure(reason) =>
                println(s"Failed to get the number")
                nFailures += 1
                Behaviors.same
        }
    }

    // Stateless - fully functional - we do not have mutable states and no execution context
    def phoneCallInitiatorV3(nPhoneCalls: Int = 0, nFailures: Int = 0): Behavior[PhoneCallProtocol] =
        Behaviors.receive {(context, message) =>
            message match {
                case FindAndCallPhoneNumber(name) =>
                    val futureNumber = Infrastructure.asyncRetrievePhoneNumber(name)
                    context.pipeToSelf(futureNumber) { // note: thread safe
                        case Success(number) => InitiatePhoneCall(number)
                        case Failure(reason) => LogPhoneCallFailure(reason)
                    }
                    Behaviors.same
                case InitiatePhoneCall(number) =>
                    println(s"Initializing phone call for number $number")
                    phoneCallInitiatorV3(nPhoneCalls+1, nFailures)

                case LogPhoneCallFailure(reason) =>
                    println(s"Failed to get the number")
                    phoneCallInitiatorV3(nPhoneCalls, nFailures+1)
            }
        }

    def main(args: Array[String]): Unit ={
        val root = ActorSystem(phoneCallInitiatorV2, "PhoneCaller")
        root ! FindAndCallPhoneNumber("Alice")
        root ! FindAndCallPhoneNumber("Akka")

        Thread.sleep(1000)
        root.terminate()
    }
}
