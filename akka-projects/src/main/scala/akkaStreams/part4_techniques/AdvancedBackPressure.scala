package rockTheJVM.akkaStreams.part4_techniques

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import java.util.Date

object AdvancedBackPressure extends App{

    implicit val system: ActorSystem = ActorSystem("AdvancedBackpressure")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    // control backpressure
    val controlledFlow = Flow[Int].map(_*2).buffer(10, OverflowStrategy.dropHead)

    case class PagerEvent(description: String, date: Date, nInstances: Int=1)
    case class Notification(email: String, pagerEvent: PagerEvent)

    val eventSource = Source(List(
        PagerEvent("Infrastructure Broke", new Date),
        PagerEvent("Illegal element in the data pipeline", new Date),
        PagerEvent("A service stopped responding", new Date),
        PagerEvent("A button does not work", new Date)
    ))

    val onCallEngineer = "gmandilaras@gmail.com"

    def sendEmail(notification: Notification): Unit =
        println(s"Dear ${notification.email} you have an event: ${notification.pagerEvent}")

    val notificationSink = Flow[PagerEvent].map(Notification(onCallEngineer, _)).to(Sink.foreach[Notification](sendEmail))
    // standard flow

    // In case we cannot backpressure (e.g. when the source is timer-based)
    // so if the source cannot be backpressured, a solution is to somehow aggregate the events and produce a single event
    // create a single notification consisting o multiple events
    def sendEmailSlowly(notification: Notification): Unit = {
        Thread.sleep(1500)
        println(s"Dear ${notification.email} you have an event: ${notification.pagerEvent}")
    }

    val aggregateNotificationFlow = Flow[PagerEvent].conflate((event1, event2) => {
        val nInstances = event1.nInstances + event2.nInstances
        PagerEvent(s"you have $nInstances notifications", new Date, nInstances)
    })
      .map(event => Notification(onCallEngineer, event))

    // because the sink is so slow, `conflate` aggregates the events into one
    // `conflate` never backpreassure decouples the upstream rate from the downstream rate,
    // so `conflate` is an alternative to backpreassure
//    eventSource.via(aggregateNotificationFlow).async.to(Sink.foreach[Notification](sendEmailSlowly))


    /*
        slow producer: extrapolate/expand
     */
    import scala.concurrent.duration._
    val slowSource = Source(Stream.from(1)).throttle(1, 1 second)
    val hungrySink = Sink.foreach(println)

    // produce new elements based on the input source
    val extrapolate = Flow[Int].extrapolate(n => Iterator.from(n))
    val repeater = Flow[Int].extrapolate(n => Iterator.continually(n))

    slowSource.via(repeater).to(hungrySink).run()

    // extrapolate create new elements when there is demand, while expand produces
    // new elements all the time
    val expand = Flow[Int].expand(n => Iterator.from(n))

}

