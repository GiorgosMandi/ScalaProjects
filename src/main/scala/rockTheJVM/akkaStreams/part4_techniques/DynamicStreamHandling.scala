package rockTheJVM.akkaStreams.part4_techniques

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.duration._
import scala.language.postfixOps

object DynamicStreamHandling extends App{
    implicit val system: ActorSystem = ActorSystem("DynamicStreamHandling")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher
    /*
     #1 - Kill switch
        Terminate your stream at any point of you choosing by exposing the killSwitch as the materialized value
     */
    val killSwitchFlow = KillSwitches.single[Int]
    val counter = Source(Stream.from(1)).throttle(1, 1 second).log("counter")
    val sink = Sink.ignore

    val killSwitchGraph = counter.viaMat(killSwitchFlow)(Keep.right).to(sink)
    val killSwitch = killSwitchGraph.run()
    system.scheduler.scheduleOnce(delay = 3 second){killSwitch.shutdown()}

    // shared kill switch
    val anotherCounter = Source(Stream.from(1)).throttle(2, 1 second).log("anotherCounter")
    val sharedKillSwitchFlow = KillSwitches.shared("OnButtonToTerminateThemAll")

    counter.viaMat(sharedKillSwitchFlow.flow)(Keep.right).to(sink).run()
    anotherCounter.viaMat(sharedKillSwitchFlow.flow)(Keep.right).to(sink).run()
    // shutdown both graphs
    system.scheduler.scheduleOnce(delay = 3 second){sharedKillSwitchFlow.shutdown()}


    /*
        #2 - Merge Hub
        Dynamically add Fan-in and fan-out ports to graph elements
     */

    val dynamicMergeHub = MergeHub.source[Int]
    val materializedSink = dynamicMergeHub.to(Sink.foreach[Int](println)).run()
    // use sink any time we like
    Source(1 to 10).runWith(materializedSink)
    counter.runWith(materializedSink)

    // BroadcastHub
    val dynamicBroadcast = BroadcastHub.sink[Int]
    val materializedSource = Source(1 to 100).runWith(dynamicBroadcast)
    materializedSource.runWith(Sink.ignore)
    materializedSource.runWith(Sink.foreach[Int](println))

    /**
     * Challenge - combine mergeHub and a broadcastHub
     *
     * Publisher-Subscriber  component
     */

    val merge = MergeHub.source[String]
    val bcast = BroadcastHub.sink[String]
    val (publisherPort, subscriberPort) = merge.toMat(bcast)(Keep.both).run()

    subscriberPort.runWith(Sink.foreach[String](e => println(s"I received $e")))
    subscriberPort.map(_.length).runWith(Sink.foreach[Int](e => println(s"I received number $e")))

    Source(List("Akka", "is", "awesome")).runWith(publisherPort)
    Source(List("I", "love", "scala")).runWith(publisherPort)
    Source.single("STREEEAMS").runWith(publisherPort)
}
