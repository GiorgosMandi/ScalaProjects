package rockTheJVM.akkaStreams.part2_primer

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

object OperatorFusion extends App {

    implicit val system: ActorSystem = ActorSystem("OperatorFusion")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val simpleSource = Source(1 to 100)
    val simpleFlow = Flow[Int].map(_ + 1)
    val simpleFlow2 = Flow[Int].map(_ * 10)
    val simpleSink = Sink.foreach[Int](println)

    // everything runs on the same actor
    simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run()

    //  Operator/ Component Fusion

    // this is the same as creating an actor invoking the specifying commands
    class SimpleActor extends Actor {
        override def receive: Receive = {
            case x: Int =>
                val x1 = x + 1
                val x2 = x1 * 10
                println(x2)
        }
    }

    val simpleActor = system.actorOf(Props[SimpleActor])
    (1 to 100).foreach(x => simpleActor ! x)

    // there is a time overhead for the async message passing between the actors of each of akka stream components.
    // So when the operations inside the stream components are fast, this operation fusion is good. This is what akka does by default
    // But operator fusion does more harm than good if the operations are more time expensive

    // complex operators
    val complexFlow = Flow[Int].map { x =>
        Thread.sleep(1000)
        x + 1
    }
    val complexFlow2 = Flow[Int].map { x =>
        Thread.sleep(1000)
        x * 10
    }

    simpleSource.via(complexFlow2).via(complexFlow2).to(simpleSink).run()

    // the whole point of akka-streams is to process all elements asynchronously in between all of these components
    // we could increase the throughput. When operators are expensive it's worth making them run separately in parallel on different actors

    // async boundary - break operator fusion
    simpleSource.via(complexFlow2).async // runs on one actor
      .via(complexFlow2).async // runs on a different actor
      .to(simpleSink) // runs on a third actor
      .run()

    // ordering guarantees with or without async
    Source(1 to 3)
      .map(element => {
          println(s"Flow A: $element"); element
      })
      .map(element => {
          println(s"Flow B: $element"); element
      })
      .map(element => {
          println(s"Flow C: $element"); element
      })
      .runWith(Sink.ignore)

    // in every step, "1" will be processed before "2" in every step
    Source(1 to 3)
      .map(element => {
          println(s"Flow A: $element"); element
      }).async
      .map(element => {
          println(s"Flow B: $element"); element
      }).async
      .map(element => {
          println(s"Flow C: $element"); element
      }).async
      .runWith(Sink.ignore)
}
