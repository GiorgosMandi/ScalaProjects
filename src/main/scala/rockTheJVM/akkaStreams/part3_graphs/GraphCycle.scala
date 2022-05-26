package rockTheJVM.akkaStreams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape, FanOutShape, FanOutShape2, SinkShape, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}
import GraphDSL.Implicits._

object GraphCycle extends App {

    implicit val system: ActorSystem = ActorSystem("OpenGraphs")
    implicit val materializer: ActorMaterializer = ActorMaterializer()


    // Graph cycle dead lock, back pressure stops the

    /*
        Solution 1: MergePreferred, merge prefers an element from its
        preferred port and always consume from there
     */

    val actualAccelerator = GraphDSL.create() { implicit builder =>
        val sourceShape = builder.add(Source(1 to 10))
        val mergerShape = builder.add(MergePreferred[Int](1))
        val incrementerShape = builder.add(Flow[Int].map { x =>
            println(s"$x")
            x + 1
        })

        sourceShape ~> mergerShape ~> incrementerShape
        mergerShape.preferred <~ incrementerShape
        ClosedShape
    }

    RunnableGraph.fromGraph(actualAccelerator).run()
}
