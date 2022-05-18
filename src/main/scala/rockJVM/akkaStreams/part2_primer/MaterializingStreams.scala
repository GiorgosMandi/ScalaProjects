package rockJVM.akkaStreams.part2_primer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStreams extends App {

    implicit val system: ActorSystem = ActorSystem("MaterializingStreams")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    import system.dispatcher

    val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
    val simpleMaterializedValue = simpleGraph.run()

    val source = Source(1 to 10)
    val sink: Sink[Int, Future[Int]] = Sink.reduce[Int]((a, b) => a + b)
    val sumFuture: Future[Int] = source.runWith(sink)
    sumFuture.onComplete {
        case Success(value) => println(s"The sum of all elements is: $value")
        case Failure(ex) => println(s"Failed to compute the sum: $ex")
    }

    val simpleSource = Source(1 to 5)
    val simpleFlow = Flow[Int].map(_ + 1)
    val simpleSink = Sink.foreach(print)
    val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)


    /**
     * Exercise:
     *
     * - return the last element of a source
     * - compute the total word count of a stream sentences
     */

    val elements: List[String] = List("this is a sentence", "I am learning akka-streams", "I watch RockTheJVM course", "Scala is awesome", "Akka is awesome")
    val lastElement: Future[String] = Source(elements).runWith(Sink.last[String])
    lastElement.onComplete {
        case Success(sentence) => println(s"Last sentence is: $sentence")
        case Failure(_) => println(s"Failed to find last sentence")
    }

    val wordCount = Source(elements)
      .map(sentence => sentence.split("\\s+").length)
      .fold(0)((count, words) => words + count)
      .runWith(Sink.head)
    wordCount.onComplete {
        case Success(count) => println(s"Total words: $count")
        case Failure(_) => println(s"Failed to find the total words")
    }
}
