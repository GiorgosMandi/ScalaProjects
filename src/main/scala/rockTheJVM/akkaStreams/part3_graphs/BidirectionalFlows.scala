package rockTheJVM.akkaStreams.part3_graphs

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, BidiShape, ClosedShape, FanOutShape, FanOutShape2, SinkShape, UniformFanInShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, RunnableGraph, Sink, Source, Zip, ZipWith}
import GraphDSL.Implicits._

object BidirectionalFlows extends App {
    implicit val system: ActorSystem = ActorSystem("OpenGraphs")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    def encrypt(n: Int)(message: String): String = message.map(c => (c + n).toChar)
    def decrypt(n: Int)(message: String): String = message.map(c => (c - n).toChar)

    val bidiCryptoStaticGraph = GraphDSL.create(){ implicit builder =>
        val encryptionFlowShape = builder.add(Flow[String].map(encrypt(3)))
        val decryptionFlowShape = builder.add(Flow[String].map(decrypt(3)))

        BidiShape.fromFlows(encryptionFlowShape, decryptionFlowShape)
    }

    val unencryptedMessage = List("akka", "is", "awesome", "testing", "bidirectional", "streams" )
    val unencryptedSource = Source(unencryptedMessage)
    val encryptedSource = Source(unencryptedMessage.map(msg => encrypt(3)(msg)))

    val cryptoBidiGraph = RunnableGraph.fromGraph(
        GraphDSL.create() { implicit builder =>
            val unencryptedSourceShape = builder.add(unencryptedSource)
            val encryptedSourceShape = builder.add(encryptedSource)
            val bidi = builder.add(bidiCryptoStaticGraph)
            val encryptedSinkShape = builder.add(Sink.foreach[String](s => println(s"Encrypted: $s")))
            val decryptedSinkShape = builder.add(Sink.foreach[String](s => println(s"Decrypted: $s")))

            unencryptedSourceShape ~> bidi.in1 ; bidi.out1 ~> encryptedSinkShape
            decryptedSinkShape <~ bidi.out2 ; bidi.in2 <~ encryptedSourceShape

            ClosedShape
        }
    )

    cryptoBidiGraph.run()
}
