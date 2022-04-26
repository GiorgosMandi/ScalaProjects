package rockJVM.akkaStreams.part2_primer

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App{
    implicit val actorSystem: ActorSystem = ActorSystem("FirstPrinciples")
    implicit val materializer: ActorMaterializer = ActorMaterializer() // takes implicitly the actorSystem

    // Source
    val source: Source[Int, NotUsed] = Source(1 to 10)

    // Sink
    val sink: Sink[Int, Future[Done]] = Sink.foreach[Int](println)

    val graph: RunnableGraph[NotUsed] = source.to(sink)

    graph.run()


    //flows: transform elements
    val flow = Flow[Int].map(x => x+1)

    // (source -> flow) -> sink
    val sourceWithFlow = source.via(flow)
    sourceWithFlow.to(sink).run()

    // source -> (flow -> sink)
    val flowWithSink = flow.to(sink)
    source.to(flowWithSink).run()

    // source -> flow -> sink
    source.via(flow).to(sink).run()

    // various kinds of sources
    val finiteSource = Source(List(1,2,3))
    val finiteSource_ = Source.single(1)
    val emptySource = Source.empty[Int]
    val infiniteSource = Source(Stream.from(1)) // stream of collection (not Akka)

    import scala.concurrent.ExecutionContext.Implicits.global
    val fromFutures = Source.fromFuture(Future(42))

    // various kinds of Sinks
    val noSink = Sink.ignore
    val forEachSink = Sink.foreach[String](println)
    val headSink = Sink.head[Int]
    val foldSink = Sink.fold[Int, Int](0)((a,b) => a + b)

    // various flows
    val mapFlow = Flow[Int].map(_+1)
    val takeFlow = Flow[Int].take(5)

    val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)
    doubleFlowGraph.run()

    // syntactic sugars
    val mapSource = Source(1 to 10).map(x => x * 2).runForeach(println)
    // run string directly
    mapSource


    /**
     * Exercise: Create a stream with name of people, then you will keep the first two names with length > 5 characters
     */

    val streamExc = Source(List("George", "Daniel", "Irene", "Mike", "Gabriella", "Jessica", "Paul", "Emmanouil", "Helene", "Theodore"))
      .filter(_.length > 5)
      .take(2)
      .runForeach(println)
}
