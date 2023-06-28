package rockTheJVM.akkaStreams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape, FanOutShape, FanOutShape2, FlowShape, SinkShape, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, MergePreferred, RunnableGraph, Sink, Source, Zip, ZipWith}
import GraphDSL.Implicits._
import cats.kernel.instances.BigIntOrder

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

//    RunnableGraph.fromGraph(actualAccelerator).run()

    /**
     * Challenge: create a fan-in shape
     * - takes two inputs which will be fed with exactly one number
     * - output will emit an INFINITE FIBONACCI sequence  based of these two numbers
     *
     * Hint: ZipWith, and Cycles, MergePreferred
     */


    val fibonacciShape = GraphDSL.create() {
        implicit builder =>
            val zipShape = builder.add(Zip[BigInt, BigInt])
            val mergerPreferredShape = builder.add(MergePreferred[(BigInt, BigInt)](1))
            val fibonacciLogic = builder.add(Flow[(BigInt, BigInt)].map{ case (last, previous) =>
                Thread.sleep(1000)
                (previous + last, last)
            })
            val broadcastShape = builder.add(Broadcast[(BigInt, BigInt)](2))
            val extractLast = builder.add(Flow[(BigInt, BigInt)].map{ case (last, previous) => last})

            zipShape.out ~> mergerPreferredShape ~> fibonacciLogic ~> broadcastShape ~> extractLast
                            mergerPreferredShape.preferred      <~      broadcastShape

            UniformFanInShape(extractLast.out, zipShape.in0, zipShape.in1)
    }



    val fibonacciGraph = RunnableGraph.fromGraph({
        GraphDSL.create() {implicit builder =>
            val sourceF0 = builder.add(Source.single[BigInt](1))
            val sourceF1 = builder.add(Source.single[BigInt](1))
            val sink = builder.add(Sink.foreach[BigInt](f => println(s"Fibonacci: $f")))
            val fibonacciGraph = builder.add(fibonacciShape)

            sourceF0 ~> fibonacciGraph.in(0)
            sourceF1 ~> fibonacciGraph.in(1)
            fibonacciGraph.out ~> sink
            ClosedShape
        }
    })
    fibonacciGraph.run()
}
