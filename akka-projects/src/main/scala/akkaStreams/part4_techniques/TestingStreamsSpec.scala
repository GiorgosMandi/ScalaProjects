package rockTheJVM.akkaStreams.part4_techniques

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.{TestPublisher, TestSubscriber}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{TestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import java.lang
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}


class TestingStreamsSpec extends TestKit(ActorSystem("TestingAkkaStream"))
  with AnyWordSpecLike
  with BeforeAndAfterAll {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

    "A simple stream" should {
        "satisfy basic assertion" in {
            val simpleSource = Source(1 to 10)
            val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)

            val sumFuture: Future[Int] = simpleSource.toMat(simpleSink)(Keep.right).run()
            val sum = Await.result(sumFuture, 2 seconds)
            assert(sum == 55)
        }

        "integrate with test actors via materialized values" in {
            import akka.pattern.pipe
            import system.dispatcher

            val simpleSource = Source(1 to 10)
            val simpleSink = Sink.fold(0)((a: Int, b: Int) => a + b)

            val testProbe = TestProbe()
            simpleSource.toMat(simpleSink)(Keep.right).run().pipeTo(testProbe.ref)

            testProbe.expectMsg(55)
        }

        "integrate with test-actor-based sink" in {
            val simpleSource = Source(1 to 5)
            val flow = Flow[Int].scan[Int](0)(_ + _) // like fold, but emits the intermediate values: 0 , 1 , 3, 6, 10, 15
            val streamUnderTest = simpleSource.via(flow)

            val testProbe = TestProbe()
            val probeSink = Sink.actorRef(testProbe.ref, "completionMessage")

            streamUnderTest.to(probeSink).run()

            testProbe.expectMsgAllOf(0, 1, 3, 6, 10, 15)
        }

        "integrate with Streams testkit Sink" in {
            val sourceUnderTest = Source(1 to 5).map(_ * 2)

            val testSink: Sink[Int, TestSubscriber.Probe[Int]] = TestSink.probe[Int]
            val materializedSourceValue: TestSubscriber.Probe[Int] = sourceUnderTest.runWith(testSink)

            materializedSourceValue
              .request(5)
              .expectNext(2, 4, 6, 8, 10)
              .expectComplete()
        }

        "integrate with Streams testkit Source" in {

            val sinkUnderTest = Sink.foreach[Int] {
                case 13 => throw new lang.RuntimeException()
                case _ =>
            }

            val testSource = TestSource.probe[Int]
            val materialized: (TestPublisher.Probe[Int], Future[Done]) = testSource.toMat(sinkUnderTest)(Keep.both).run()
            val (testPublisher, resultFuture) = materialized

            testPublisher
              .sendNext(1)
              .sendNext(5)
              .sendNext(13)
              .sendComplete()

            resultFuture.onComplete {
                case Success(_) => fail("The stream should have crashed on value 13")
                case Failure(_) => // ok
            }(system.dispatcher)
        }

        "integrate with Streams test Source AND test Sink" in {
            val flowUnderTest = Flow[Int].map[Int](_*2)

            val testSource = TestSource.probe[Int]
            val testSink: Sink[Int, TestSubscriber.Probe[Int]] = TestSink.probe[Int]

            val materialized: (TestPublisher.Probe[Int], TestSubscriber.Probe[Int]) = testSource.via(flowUnderTest).toMat(testSink)(Keep.both).run()
            val (publisher, subscriber) = materialized

            publisher
              .sendNext(1)
              .sendNext(3)
              .sendNext(5)
              .sendNext(8)
              .sendComplete()

            subscriber
              .request(4)
              .expectNext(2, 6, 10, 16)
              .expectComplete()
        }
    }
}
