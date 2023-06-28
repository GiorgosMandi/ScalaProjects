package rockTheJVM.akkaStreams.part5_advanced

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.util.{Failure, Success}

object Substreams extends App {
    implicit val system: ActorSystem = ActorSystem("Substreams")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    // 1 - grouping a stream by a certain function
    // group by will create a new substream for each unique key
    // each substream is processed in parallel
    val wordsSource = Source(List("Akka", "is", "amazing", "learning", "substreams"))
    val groups = wordsSource.groupBy(30, word =>  if (word.isEmpty) '\0' else word.toLowerCase().charAt(0))
    val groupCountGraph = groups.to(Sink.fold(0)((count, word) => {
        val newCount = count + 1
        println(s"I just received $word, count is $newCount")
        newCount
    }))
//    groupCountGraph.run()

    // 2 - merge substreams back
    val textSource = Source(List(
        "I love Akka streams",
        "this is amazing",
        "learning from Rock the JVM"
    ))

    val mergeSubgraph = textSource
      .groupBy(2, string => string.length % 2)
      .map(_.length)
      .mergeSubstreamsWithParallelism(2)
      .toMat(Sink.reduce[Int](_+_))(Keep.right)
    val totalCharCountFuture = mergeSubgraph.run()
    totalCharCountFuture.onComplete {
        case Failure(exception) => println(s"Char computation failed: $exception")
        case Success(value) => println(s"Total char count: $value")
    }

    // 3 - splitting a stream into substreams, when a condition is met
    val text = "I love Akka streams\n" + "this is amazing\n" + "learning from Rock the JVM\n"
    val anotherCharCountGraph = Source(text.toList)
      .splitWhen(c => c == '\n')
      .filter(_ != '\n')
      .map(_ => 1)
      .mergeSubstreams
      .toMat(Sink.reduce[Int](_+_))(Keep.right)

//    val anotherCharCountFuture = anotherCharCountGraph.run


    // 4 - Flattening
    val simpleSource = Source(1 to 5)
    simpleSource.
      flatMapConcat(x => Source(x to (3 * x)))
      .runWith(Sink.foreach(println))

    // flatten with ordering
    simpleSource.
      flatMapMerge(2, x => Source(x to (3 * x)))
      .runWith(Sink.foreach(println))
}
