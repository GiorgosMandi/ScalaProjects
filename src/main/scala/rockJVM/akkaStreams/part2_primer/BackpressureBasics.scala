package rockJVM.akkaStreams.part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}

import scala.language.postfixOps

object BackpressureBasics extends App {

    implicit val system: ActorSystem = ActorSystem("OperatorFusion")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val fastSource = Source(1 to 100)
    val slowSink = Sink.foreach[Int] { x =>
        Thread.sleep(1000)
        println(s"Sink $x")
    }

    // not backpressure, as the operation is fused in a single Actor
    //    fastSource.to(slowSink).run()

    // backpressure - the source is slow down
    fastSource.async.to(slowSink).run()

    // Buffering: the flow is slowed down, so the source responds in batches
    val simpleFlow = Flow[Int].map { x =>
        println(s"Incoming $x");
        x + 1
    }
    fastSource.async.via(simpleFlow).async.to(slowSink).run()

    /*
    reactions to backpressure:
        - slow down (the source reduces the rate of productions)
        - buffer elements until there is more demand
        - drop down elements from the buffer if it overflows
        - last resort: kill the whole stream (failure)
     */

    val bufferedFlow = Flow[Int].buffer(10, overflowStrategy = OverflowStrategy.dropHead).
      map { x =>
          println(s"Incoming $x");
          x + 1
      }

    /*
    OverflowStrategy:
        - drop head -> drop oldest element in the buffer
            - 1-16 : no pressure, buffered in the sink
            -17-26: flow will buffer, flow will start dropping the oldest elements.
            -26-100: flow will always drop the oldest element
                - 991 - 1000 will be the newest elements, which will survive to the sink
        - drop tail -> drop the newest elements of the buffer
        - drop new -> keep buffer drop the incoming element
        - drop the entire buffer
        - backpressure signal
        - fail
     */

    // throttling

    import scala.concurrent.duration._
    // spawn 2 elements per 1 second
    fastSource.throttle(2, 1 second)
}


