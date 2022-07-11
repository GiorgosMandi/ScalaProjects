package rockTheJVM.akkaStreams.part4_techniques

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object IntegratingWithActors extends App {

    implicit val system: ActorSystem = ActorSystem("OpenGraphs")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    class SimpleActor extends Actor with ActorLogging {
        override def receive: Receive = {
            case s: String =>
                log.info(s"Received String: $s")
                sender() ! s"$s$s"
            case n: Int =>
                log.info(s"Received number: $n")
                sender() ! (n * 2)
            case _ =>
                log.warning("Received an non-covered case")
        }
    }

    val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")
    val numberSource = Source(1 to 10)

    /*
        Actor as a Flow: the Ask Pattern
            In the Ask pattern of an Actor, you ask an actor (send a message) and
            expect a reply as a Future. This flow is based on these Futures (futures needs timeout)
             Parallelism says how many messages can be in the actors mailbox before starting back-pressuring
             (Actors are single-threaded)
     */
    implicit val timeout: Timeout = Timeout(2 second)
    val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

    numberSource.via(actorBasedFlow).to(Sink.ignore).run()
    // equivalent
    numberSource.ask(parallelism = 4)(simpleActor).to(Sink.ignore).run()

    /*
        Actor as a Source
     */
    val actorPoweredSource = Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
    // the materialized value of the source in the ActorRef
    val materializedActorRef: ActorRef = actorPoweredSource.to(Sink.foreach[Int](n => println(s"Actor powered flow got number: $n"))).run()
    materializedActorRef ! 10
    // to terminate the stream we need a specialized message
    materializedActorRef ! akka.actor.Status.Success("complete")

    /*
        Actor as a Destination/Sink
            - an init message
            - an ACK message to confirm the reception (the absence on ACK will be interpreted as back-pressure)
            - a complete message
            - a function to generate message in case the stream throws an exception
    */

    case object StreamInit

    case object StreamAck

    case object StreamComplete

    case class StreamFail(ex: Throwable)

    class DestinationActor extends Actor with ActorLogging {

        override def receive: Receive = {
            case StreamInit =>
                log.info("Stream Initialized")
                sender() ! StreamAck
            case StreamComplete =>
                log.info("Stream complete")
                context.stop(self)
            case StreamFail(ex) =>
                log.warning(s"Stream Failed: $ex")
            case message =>
                log.info(s"Message $message has come to its final destination.")
                sender() ! StreamAck
        }
    }

    val destinationActor = system.actorOf(Props[DestinationActor], "destinationActor")
    val actorPoweredSink = Sink.actorRefWithAck[Int](
        destinationActor,
        onInitMessage = StreamInit,
        onCompleteMessage = StreamComplete,
        ackMessage = StreamAck,
        onFailureMessage = throwable => StreamFail(throwable)
    )

    Source(1 to 10).to(actorPoweredSink).run()
}
