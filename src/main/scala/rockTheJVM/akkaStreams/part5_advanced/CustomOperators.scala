package rockTheJVM.akkaStreams.part5_advanced

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, Attributes, FlowShape, Inlet, Outlet, SinkShape, SourceShape}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

import scala.collection.mutable
import scala.util.Random

object CustomOperators extends App{
    implicit val system: ActorSystem = ActorSystem("CustomOperators")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    // 1 - A custom source that emits random number until cancelled

    class RandomNumberGenerator(max: Int) extends GraphStage[/*step1: define the shape*/SourceShape[Int]] {
        // step2: define the ports and the component-specific members
        val outPort: Outlet[Int] = Outlet[Int]("RandomGenerator")
        val random = new Random()

        // step3: construct a new  shape
        override def shape: SourceShape[Int] = SourceShape(outPort)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
            // step4: Define mutable state by setting handlers on the ports
            // implement logic here
            setHandler(outPort, new OutHandler {
                // when there is demand from downstream
                override def onPull(): Unit = {
                    val nextNumber = random.nextInt(max)
                    // push it down to outPort
                    push(outPort, nextNumber)
                }
            })
        }
    }

    val randomNumberGenerator = Source.fromGraph(new RandomNumberGenerator(100))
    val RNGGraph = randomNumberGenerator.to(Sink.foreach[Int](println))
//    RNGGraph.run()


    // 2 - a custom sink that prints elements of batches of a given size
    class Batcher(size: Int) extends GraphStage[SinkShape[Int]] {

        val inPort: Inlet[Int] = Inlet[Int]("batcher")

        override def shape: SinkShape[Int] = SinkShape[Int](inPort)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape){

            val batch = new mutable.Queue[Int]

            override def preStart(): Unit = {
                // send signal to the inPort (i.e., the source, input flow) to send an element
                pull(inPort)
            }

            setHandler(inPort, new InHandler {
                override def onPush(): Unit = {
                    val nextElement: Int = grab(inPort)
                    batch.enqueue(nextElement)
                    if (batch.size >= size){
                        println(s"New Batch: ${batch.dequeueAll(_ => true).mkString("[", ", ", "]")}")
                    }
                    // send demand upstream
                    pull(inPort)
                }
                override def onUpstreamFinish(): Unit = {
                    if (batch.nonEmpty)
                        println(s"New Batch: ${batch.dequeueAll(_ => true).mkString("[", ", ", "]")}")
                    println("Batch Finished")
                }

                override def onUpstreamFailure(ex: Throwable): Unit = {
                    if (batch.nonEmpty)
                        println(s"New Batch: ${batch.dequeueAll(_ => true).mkString("[", ", ", "]")}")
                    println("Batch Failed")
                }
            })
        }
    }

    val batcherSink = Sink.fromGraph(new Batcher(5))
    val batcherGraph = randomNumberGenerator.to(batcherSink)
//    batcherGraph.run()


    /**
     * Exercise:
     * a custom flow, a simple Filter flow
     * - 2 ports, an input and an output port
     */
    class SimpleFilter[T](predicate: T => Boolean) extends GraphStage[FlowShape[T, T]] {

        val inPort: Inlet[T] = Inlet[T]("Filterer")
        val outPort: Outlet[T] = Outlet[T]("Filterer")

        override def shape: FlowShape[T, T] = FlowShape[T, T](inPort, outPort)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

            setHandler(outPort, new OutHandler {
                override def onPull(): Unit = pull(inPort)
            })

            setHandler(inPort, new InHandler {
                override def onPush(): Unit = {
                    try{
                        val nextElement = grab(inPort)
                        if (predicate(nextElement)) {
                            push(outPort, nextElement) // pass it on
                        }
                        else {
                            pull(inPort) // ask for another element
                        }
                    }catch {
                        case e: Throwable => failStage(e)
                    }
                }
            })
        }
    }

    val myFilter = Flow.fromGraph(new SimpleFilter[Int](_ > 50))
    val graph = randomNumberGenerator.via(myFilter).to(batcherSink)
    // backpreassure is Out Of The Box (OOTB)
    graph.run()

    /**
     * Materialized values in graph stage
     */

    // 3 - a flow that counts the number of elements that go through it
    // CHECK VIDEO
}

