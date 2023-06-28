package rockTheJVM.akkaStreams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FlowShape, SinkShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import akka.stream.scaladsl.GraphDSL.Implicits._
object OpenGraphs extends App{

    implicit val system: ActorSystem = ActorSystem("OpenGraphs")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val firstSource = Source(1 to 10)
    val secondSource = Source(42 to 1000)
    val sourceGraph = Source.fromGraph({
        GraphDSL.create(){implicit builder =>

            val concat = builder.add(Concat[Int](2))
            firstSource ~> concat
            secondSource ~> concat

            SourceShape(concat.out)
        }
    })

    sourceGraph.to(Sink.foreach(println)).run()

    /*
        complexSink
     */
    val sink1 = Sink.foreach[Int](x => println(s"Meaningful thing 1: $x"))
    val sink2 = Sink.foreach[Int](x => println(s"Meaningful thing 2: $x"))
    val sinkGraph = Sink.fromGraph({
            GraphDSL.create() { implicit builder =>
                val broadcast = builder.add(Broadcast[Int](2))
                broadcast ~> sink1
                broadcast ~> sink2
                SinkShape(broadcast.in)
            }
    })

    /*
        Complex flow
        Compose two other flows, x+1 and x*10
     */

    val incrementer = Flow[Int].map(_+1)
    val multiplier = Flow[Int].map(_*10)

    val flowGraph = Flow.fromGraph({
        GraphDSL.create() { implicit builder =>
            // everything on GraphDSL operates on shapes

            // define shapes
            val incrementerShape = builder.add(incrementer)
            val multiplierShape = builder.add(multiplier)

            // connect shapes
            incrementerShape ~> multiplierShape
            FlowShape(incrementerShape.in, multiplierShape.out)
        }
    })

    sourceGraph.via(flowGraph).to(sinkGraph).run()
}
