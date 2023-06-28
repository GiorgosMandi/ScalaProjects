package rockTheJVM.akkaStreams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, FanOutShape, FanOutShape2, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink, Source, Zip, ZipWith}
import GraphDSL.Implicits._

import java.util.Date
import javax.imageio.spi.ServiceRegistry.Filter

object MoreOpenGraphs extends App {

    implicit val system: ActorSystem = ActorSystem("OpenGraphs")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    /*
        Max3 operator, find the max among three sources
     */

    // step 1
    val max3 = GraphDSL.create(){ implicit builder =>

        // step 2 - define aux shapes
        val max1 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))
        val max2 = builder.add(ZipWith[Int, Int, Int]((a, b) => Math.max(a, b)))

        // step 3 - tye the components
        max1.out ~> max2.in0

        UniformFanInShape(max2.out, max1.in0, max1.in1, max2.in1)
    }

    val source1 = Source(1 to 10)
    val source2 = Source(1 to 10).map(_ => 5)
    val source3 = Source((1 to 10).reverse)
    val maxSink = Sink.foreach[Int](x => println(s"Max is: $x"))

    val max3RunnableGraph = RunnableGraph.fromGraph(
        GraphDSL.create(){ implicit builder =>
            val max3Shape = builder.add(max3)

            source1 ~> max3Shape.in(0)
            source2 ~> max3Shape.in(1)
            source3 ~> max3Shape.in(2)

            max3Shape.out ~> maxSink
            ClosedShape
        }
    )

//    max3RunnableGraph.run()

    /*
        Suspicious transaction detection
     */
    case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)
    val transactionSource = Source(List(
        Transaction("526884", "George", "Jack", 100, new Date),
        Transaction("436542", "Marie", "Elisabeth", 100000, new Date),
        Transaction("876342", "Jack", "Marie", 4322, new Date),
        Transaction("543265", "George", "Daniel", 542311, new Date),
        Transaction("876523", "Daniel", "George", 4421, new Date),
    ))
    val suspiciousAmount = 10000
    val bankProcessor = Sink.foreach[Transaction](println)
    val suspiciousAnalysisService = Sink.foreach[String](trxId => println(s"Suspicious transaction: $trxId"))

    val suspiciousTransactionStaticGraph = GraphDSL.create(){ implicit builder =>
        val broadcast = builder.add(Broadcast[Transaction](2))
        val suspiciousFilter = builder.add(Flow[Transaction].filter(trx => trx.amount > suspiciousAmount))
        val trxIdExtractor = builder.add(Flow[Transaction].map(trx => trx.id))

        broadcast.out(0) ~> suspiciousFilter ~> trxIdExtractor

        new FanOutShape2(broadcast.in, broadcast.out(1), trxIdExtractor.out)
    }

    val suspiciousRunnableGraph = RunnableGraph.fromGraph({
        GraphDSL.create(){ implicit builder =>
            val suspiciousTransactionComponent = builder.add(suspiciousTransactionStaticGraph)

            transactionSource ~> suspiciousTransactionComponent.in
            suspiciousTransactionComponent.out0 ~> bankProcessor
            suspiciousTransactionComponent.out1 ~> suspiciousAnalysisService

            ClosedShape
        }
    })
    suspiciousRunnableGraph.run()

}

