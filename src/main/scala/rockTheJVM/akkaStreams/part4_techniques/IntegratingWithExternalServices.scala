package main.scala.rockTheJVM.akkaStreams.part4_techniques

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future

object IntegratingWithExternalServices extends App{
    implicit val system: ActorSystem = ActorSystem("IntegratingWithExternalServices")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // import system.dispatcher // not recommended, you should use a different dispatcher for Futures
    // different dispatcher are defined in the application.conf
    implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")


    def genericExtService[A, B](element: A): Future[B] = ???

    case class PagerEvent(application: String, description: String, date: Date)

    val eventSource = Source(List(
        PagerEvent("AkkaInfra", "Infrastructure Broke", new Date),
        PagerEvent("FastDataPipeline", "Illegal element in the data pipeline", new Date),
        PagerEvent("AkkaInfra", "A service stopped responding", new Date),
        PagerEvent("SuperFrontend", "A button does not work", new Date)
    ))

    object PagerService {
        val engineers = List("Daniel", "John", "Lady Gaga")
        val emails = Map("Daniel" -> "daniel@rthjvm.com", "John" ->"john@rthjvm.com", "Lady Gaga" -> "lgaga@rthjvm.com")

        def processEvent(pagerEvent: PagerEvent) = Future {
            val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24*3600)) % engineers.length
            val engineer = engineers(engineerIndex.toInt)
            val engineerEmail = emails(engineer)

            println(s"Sending engineer $engineer a high priority notification: $pagerEvent")
            Thread.sleep(1000)

            engineerEmail
        }
    }

    val infraEvents = eventSource.filter(_.application == "AkkaInfra")
    val pagedEngineersEmails = infraEvents.mapAsync(parallelism = 4)(event => PagerService.processEvent(event))
    // guarantees the relative order of the elements
    //val pagedEngineers_ = infraEvents.mapAsyncUnordered(parallelism = 4)(event => PagerService.processEvent(event))
    // `mapAsyncUnordered` faster but does not guarantee order

    val pagedEmailSink = Sink.foreach[String](email => println(s"Succesfully sent notification to $email"))

    // pagedEngineersEmails.to(pagedEmailSink).run()

    // Similarly with Futures, we can work with Actors

    class PagerActor extends Actor{
        val engineers = List("Daniel", "John", "Lady Gaga")
        val emails = Map("Daniel" -> "daniel@rthjvm.com", "John" -> "john@rthjvm.com", "Lady Gaga" -> "lgaga@rthjvm.com")

        def processEvent(pagerEvent: PagerEvent): String = {
            val engineerIndex = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
            val engineer = engineers(engineerIndex.toInt)
            val engineerEmail = emails(engineer)
            println(s"Sending engineer $engineer a high priority notification: $pagerEvent")
            Thread.sleep(1000)
            engineerEmail
        }

        override def receive: Receive = {
            case pagerEvent: PagerEvent => sender ! processEvent(pagerEvent)
        }
    }

    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout = Timeout(3 second)
    val pagerActor = system .actorOf(Props[PagerActor], "PagerActor")
    val alternativePagedEngineersEmails = infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
    alternativePagedEngineersEmails.to(pagedEmailSink).run()

}
