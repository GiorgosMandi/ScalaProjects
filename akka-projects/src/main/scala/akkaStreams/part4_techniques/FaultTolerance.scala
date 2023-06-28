package rockTheJVM.akkaStreams.part4_techniques

import akka.actor.ActorSystem
import akka.stream.Supervision.{Resume, Stop}
import akka.stream.scaladsl.{RestartSource, Sink, Source}
import akka.stream.{ActorAttributes, ActorMaterializer}

import scala.util.Random

object FaultTolerance extends App {

    implicit val system: ActorSystem = ActorSystem("FaultTolerance")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    // 1 - logging
    val faultySource = Source(1 to 10).map(n => if (n == 6) throw new RuntimeException else n)
    val loggedGraph = faultySource.log("trackingElements").to(Sink.ignore)
    //    loggedGraph.run()

    // 2 - gracefully terminate a string
    val recoverableGraph = faultySource.recover {
        case _: RuntimeException => Int.MinValue
    }
      .log("gracefulSource")
      .to(Sink.ignore)

    //    recoverableGraph.run()

    // 3 - recover with another stream
    val recoverWithRetriesGraph = faultySource.recoverWithRetries(3, {
        case _: RuntimeException => Source(90 to 99)
    })
      .log("recoverWithRetries")
      .to(Sink.ignore)
    recoverWithRetriesGraph.run()

    // 4 - backoff supervision
    // Warning: requires different declaration than the one specified in the cource
        import scala.concurrent.duration._
        val restartSource = RestartSource.onFailuresWithBackoff(
            minBackoff = 2 second,
            maxBackoff = 30 second,
            randomFactor = 0.2,
            maxRestarts = 10
        )(() => {
            val randomNumber = new Random().nextInt(20)
            Source(1 to 10).map(n => if (n == randomNumber) throw new RuntimeException else n)
        })

        val restartGraph = restartSource
          .log("recoverWithRetries")
          .to(Sink.ignore)

//        restartGraph.run()


    val numbers = Source(1 to 20).map(n => if (n == 13) throw new RuntimeException else n).log("supervision")
    val supervisedNumbers = numbers.withAttributes(ActorAttributes.supervisionStrategy {
        /*
            Resume = skip faulty elements
            Stop = stop the stream
            Restart = resume + clear internal state
         */
        case _: RuntimeException => Resume
        case _ => Stop
    })

    val supervisedGraph = supervisedNumbers.to(Sink.ignore)
    supervisedGraph.run()
}

