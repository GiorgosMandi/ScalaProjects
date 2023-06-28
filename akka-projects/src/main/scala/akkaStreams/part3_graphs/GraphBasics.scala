package rockTheJVM.akkaStreams.part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{Balance, Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source, Zip}

import scala.language.postfixOps

object GraphBasics extends App{

    implicit val system: ActorSystem = ActorSystem("GraphBasics")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val input = Source(1 to 1000)
    val incrementer = Flow[Int].map(x => x + 1)
    val multiplier = Flow[Int].map(x => x * 10)
    val output = Sink.foreach[(Int, Int)](println)

    // step 1 - setting up the fundamentals of the graph
    val graph = RunnableGraph.fromGraph(GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] => // builder is a MUTABLE data structure

        // step 2 - add the necessary components of the graph
        val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator
        val zip = builder.add(Zip[Int, Int])

        // step 3 - tying up the components
        input ~> broadcast

        broadcast.out(0) ~> incrementer ~> zip.in0
        broadcast.out(1) ~> multiplier ~> zip.in1

        zip.out ~> output

        ClosedShape // Freeze builder -> builder becomes IMMUTABLE
    })

//    graph.run()


    /**
     * exercise 1: feed a source into two sinks at the same time
     */

    val output1 = Sink.foreach[Int](x => println(s"Sink_1: $x"))
    val output2 = Sink.foreach[Int](x => println(s"Sink_2: $x"))

    val graphEx1 = RunnableGraph.fromGraph(GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] =>

        val broadcast = builder.add(Broadcast[Int](2))
        input   ~>  broadcast ~> output1 // implicit port numbering
                    broadcast ~> output2
        ClosedShape
    })
//    graphEx1.run()

    /**
     * Exercise 2:
     *
     * slow source ->                                   -> sink 1
     *                  ->  merge ->   ->  balance ->
     * fast source ->                                   -> sink 2
     *
     */

    import scala.concurrent.duration._
    val fastSource = Source(1 to 100 ).filter(_ % 2 == 0).throttle(4, 1 second)
    val slowSource = Source(1 to 100).filter(_ % 2 == 1).throttle(2, 1 second)
    val graphEx2 = RunnableGraph.fromGraph(GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] =>

        val merge = builder.add(Merge[Int](2))
        val balance = builder.add(Balance[Int](2, waitForAllDownstreams=true))
        fastSource ~> merge
        slowSource ~> merge
        merge ~> balance
        balance ~> output1
        balance ~> output2

        ClosedShape
    })
    graphEx2.run()
}
