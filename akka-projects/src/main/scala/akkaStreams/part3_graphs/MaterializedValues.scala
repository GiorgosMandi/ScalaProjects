package rockTheJVM.akkaStreams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape, FanOutShape2, SinkShape, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source, Zip, ZipWith}
import GraphDSL.Implicits._

import scala.util.{Failure, Success}


object MaterializedValues extends App {

    implicit val system: ActorSystem = ActorSystem("OpenGraphs")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    /*
        Composite component (Sink)
        - print all the lowercase strings
        - count the strings shorter than 5 (< 5 chars)
     */

    val wordSource = Source(List("Akka", "is", "awesome", "Rock", "the", "JVM"))
    val printer = Sink.foreach(println)
    val counter = Sink.fold[Int, String](0)((count, _) => count + 1)

    val complexWordSink = Sink.fromGraph({
        GraphDSL.create(counter){ implicit builder => counterShape =>
            val broadcast = builder.add(Broadcast[String](2))
            val lowercaseFilter = builder.add(Flow[String].filter(word => word == word.toLowerCase()))
            val sizeFilter = builder.add(Flow[String].filter(word => word.length < 5))

            broadcast.out(0) ~> lowercaseFilter ~> printer
            broadcast.out(1) ~> sizeFilter ~> counterShape
            SinkShape(broadcast.in)
        }
    })

    import system.dispatcher
    val wordCountFuture = wordSource.toMat(complexWordSink)(Keep.right).run()
    wordCountFuture.onComplete {
        case Failure(exception) => println("Counter failed")
        case Success(count) => println(s"Count of short Strings: $count")
    }
}
