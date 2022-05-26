# Akka Streams

## Akka Essentials Recap

Note:
- We don't instantiate actors, we need to create an actor through an actor system (encapsulation)
- We communicate with an actor only via messaging (`actorRef ! "Hello World from Actor"`)
- Actors can create child actors, hence there is an actor hierarchy
- We can stash messages so to process them in a future state
- The Actors' lifecycle consists of:
  - started
  - stopped
  - suspended
  - resumed
  - restarted

Properties:
- Messages are sent asynchronously
- Many actors can share a few dozens threads
- Each message is processed **AUTOMATICALLY**, hence: 
  - akka guarantees there will never have race condition on actor's state
  - There is no need for locking

--- 

## Akka Streams Concepts
Concepts: 
- publisher: emits elements asynchronously
- subscriber: receive elements
- processor: transform elements along the way
- async
- back-pressure (defined later in the course)

Note: An *Asynchronous* operation/expression is evaluated/executed in a non-well defined time and 
without blocking any running code.

Reactive Streams is a Service Provider Interface (SPI) **not an API**, which means that 
the Reactive Stream Specifications defines the concepts and how it works, including the protocols
between these components. **Akka Streams** is an API that implements the Reactive Stream Specifications

### Main Components
Source === "publisher":
- emits elements
- may or may not terminate
 
Sink === "subscriber":
- receives elements
- terminates only when the publisher terminates

Flow === "processor":
- transforms elements
- based on actors

*Note*: We build streams by connecting these components

Directions:
- upstream: to the source
- downstream: to the sink

The Source/Sink architecture looks like this:
```scala
import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}


implicit val actorSystem: ActorSystem = ActorSystem("FirstPrinciples")
implicit val materializer: ActorMaterializer = ActorMaterializer() // takes implicitly the actorSystem

// Source
val source = Source(1 to 10)
// Sink
val sink = Sink.foreach[Int](println)
val graph = source.to(sink)
//source -> sink
graph.run()
```

Similarly, we can redirect the source to a processor (Flow) which manipulates the input:

```scala
//flows: transform elements
val flow = Flow[Int].map(_+1)

// (source -> flow) -> sink
val sourceWithFlow = source.via(flow)
sourceWithFlow.to(sink).run()

// source -> (flow -> sink)
val flowWithSink = flow.to(sink)
source.to(flowWithSink).run()

// source -> flow -> sink
source.via(flow).to(sink).run()
```
Source does not allow `null` values. In case of `null`, the procedure will fail with an `NullPointerException`
We can have multiple types of Sources:
- a finite collection (i.e., singles or collections)
- empty source, that does not emit any element (`Source.empty[Int]`)
- infinite source
- sources from other thing (e.g., from `Future`)
```scala
 // various kinds of sources
val finiteSource = Source(List(1,2,3))
val finiteSource_ = Source.single(1)
val emptySource = Source.empty[Int]
val infiniteSource = Source(Stream.from(1)) // stream of collection (not Akka)

import scala.concurrent.ExecutionContext.Implicits.global
val fromFutures = Source.fromFuture(Future(42))
```

Similarly, we can have multiple types of sinks
- one that consume everything and does nothing
- a sink that processes the source
- sinks that retrieve and may return a value
- sinks that can compute values out of the element they consume

```scala
// various kinds of Sinks
val noSink = Sink.ignore
val forEachSink = Sink.foreach[String](println)
val headSink = Sink.head[Int]
val foldSink = Sink.fold[Int, Int](0)((a,b) => a + b)
```

Flows that are mapped to collection operators:
- mapper, drop, filter **BUT** not flatMap 
- taker, turns stream to a finite stream

```scala
// various flows
val mapFlow = Flow[Int].map(_+1)
val takeFlow = Flow[Int].take(5)
```

Syntactic Sugars

```scala
val names = List("George", "Daniel", "Irene", "Mike", "Gabriella", "Jessica", "Paul", "Emmanouil", "Helene", "Theodore")
val streamExc = Source(names)
    .filter(_.length > 5)
    .take(2)
    .runForeach(println)
```

## Materialization

We can store the (intermediate) values of a stream by materializing its results. Materilization will result to a `Future` which we can manage uning the `onComplete` partial function. 

By default, Akka-Streams materializes the left-most streams, if we use `run` methods it materializes the right-most. To specify which value to materialize, we use `toMat/viaMat`

```scala
val simpleSource = Source(1 to 5)
val simpleFlow = Flow[Int].map(_ + 1)
val simpleSink = Sink.foreach(println)
val graph = simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)
```

## Operation Fusion & Asyncronous

By default, all the components of a stream is performed in the same Actor, hence in the same thread. This is known as _Operation Fusion_, which means that combines all the operations and performs them at once for each element.

```scala
Source(1 to 3)
  .map(element => {println(s"Flow A: $element"); element})
  .map(element => {println(s"Flow B: $element"); element})
  .map(element => {println(s"Flow C: $element"); element})
  .runWith(Sink.ignore)
```
Results:
```
Flow A: 1
Flow B: 1
Flow C: 1
Flow A: 2
Flow B: 2
Flow C: 2
Flow A: 3
Flow B: 3
Flow C: 3
```

Note that there is a time overhead for the async message passing between the actors of each of Akka-Stream components. So when the operations inside the stream components are fast, Operation Fusion is good. This is what Akka does by default.

However, Operator Fusion does more harm than good if the operations are more time expensive, especially when the fused operation takes more than the time overhead of the async message passing of the Actors. Furthermore,  the whole point of Akka-Streams is to process all elements asynchronously in between all of these components. So, when operators are expensive it's worth making them run separately in parallel on different actors. This implemented using _Async Boundary_ which **breaks the Operator Fusion**. Example:

```scala
simpleSource.via(complexFlow2).async // runs on one actor
  .via(complexFlow2).async  // runs on a different actor
  .to(simpleSink)   // runs on a third actor
  .run()
```

```scala
Source(1 to 3)
  .map(element => {println(s"Flow A: $element"); element}).async
  .map(element => {println(s"Flow B: $element"); element}).async
  .map(element => {println(s"Flow C: $element"); element}).async
  .runWith(Sink.ignore)
```
Results:
```
Flow A: 1
Flow B: 1
Flow C: 1
Flow A: 2
Flow B: 2
Flow C: 2
Flow A: 3
Flow B: 3
Flow C: 3
```
In every case, there is a guaranteed ordering. In async, it is given that every step of each element will be processed before its next element. For instance `Flow A: 1` will always be performed before `Flow A: 2`, and similarly for all the next steps.

## Backpreassure

Elements flow as response to the demand from consumers. So consumers trigger the flow of elements in a string. So if I have a simple stream composed of source, a flow and a sink, the elements do not flow in unless there is a demand from the sink. When the sink demand flow from the Flow, the Flow will trigger demand from the Source which will result to steam flowing through the pipeline

If the consumers process the elements slower than the upstream produces them, a specific protocol will be triggered to signal the upstream to slow down. If the Flow is unable to comply it will also send a signal to the Source to limit the rate of production, consequently to slow down  the whole flow of the entire stream. This protocol is called a __Backpreassure__ and it is transparent to the programmers, although we can control it. 

Note that when there is Operation Fusion, since all pipeline components run in the same thread as a single operation, there is no backpressure. An example of backpressure is the following:

```scala
val fastSource = Source(1 to 100)
val slowSink = Sink.foreach[Int] {x =>
    Thread.sleep(1000)
    println(s"Sink $x")
fastSource.async.to(slowSink).run()
```

To better manage backpressure, we can control the behavior using buffer, so to slow down the input stream by responding with batches. 
The reactions to backpressure are the following:
- slow down (the source reduces the rate of productions)
- buffer elements until there is more demand
- drop down elements from the buffer if it overflows
- last resort: kill the whole stream (failure)

In case the buffer overflows, there are certain overflow strategies we can use.

- `dropHead`: drop oldest element of the buffer
    - 1-16 : no pressure, buffered in the sink
    - 17-26: flow will buffer, flow will start dropping the oldest elements.
    - 26-1000: flow will always drop the oldest element
    - 991 - 1000 will be the newest elements, which will survive to the sink
- `dropTail`: drop the newest elements of the buffer
- `dropNew`: keep buffer drop the incoming element
- `dropBuffer` drop the entire buffer
- `backpressure` signal backpressure
- `fail` kill pipeline

```scala
val bufferedFlow = Flow[Int].buffer(10, overflowStrategy = OverflowStrategy.dropHead).
  map{x =>
    println(s"Incoming $x");
    x + 1
}
fastSource.async.via(bufferedFlow).async.to(slowSink).run()
```
Note that there is a buffer in the sink as well.

An alternative, is to reduce the production rate in the source using `throttle`. With `throttle` we manage the production rate, by specifying the how many elements will be spawned per a specific period of time

```scala
// throttling
import scala.concurrent.duration._
// spawn 2 elements per 1 second
fastSource.throttle(2, 1 second)
```


## GraphDSL 

GraphDSL is a library which enables the design the implementation of complex graphs. For instance, here is an example of building a complex graph, using GraphDSL.

![graph-complex-flow](https://raw.githubusercontent.com/GiorgosMandi/ScalaProjects/main/src/main/resources/images/graph-complex-flow.png)

```scala
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

graph.run()
```

Another example is the following 
![graph-complex-flow](https://raw.githubusercontent.com/GiorgosMandi/ScalaProjects/main/src/main/resources/images/merge-balance.png)


```scala
import scala.concurrent.duration._
val fastSource = Source(1 to 100 ).filter(_ % 2 == 0).throttle(4, 1 second)
val slowSource = Source(1 to 100).filter(_ % 2 == 1).throttle(2, 1 second)
val graph = RunnableGraph.fromGraph(GraphDSL.create(){ implicit builder: GraphDSL.Builder[NotUsed] =>

    val merge = builder.add(Merge[Int](2))
    val balance = builder.add(Balance[Int](2, waitForAllDownstreams=true))
    fastSource ~> merge
    slowSource ~> merge
    merge ~> balance
    balance ~> output1
    balance ~> output2

    ClosedShape
})
graph.run()

```

`Balance`: Fan-out the stream to several streams. Each upstream element is emitted to the first available downstream consumer.

`Broadcast`: Emit each incoming element each of n outputs.

`Merge`: Merge multiple sources. Picks elements randomly if all sources has elements ready.

`Zip`: Combine the elements of 2 streams into a stream of tuples.

We can create more `Shapes` using GraphDSL like `SourceShapes`, `SinkShapes` and `FlowShapes`

```scala
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

```

Another great example, is the following. Here we build a suspicious transaction detection pipeline:

```scala
case class Transaction(id: String, source: String, recipient: String, amount: Int, date: Date)
val transactionSource = Source(List(
    Transaction("526884", "George", "Teo", 100, new Date),
    Transaction("436542", "Sofia", "Helen", 100000, new Date),
    Transaction("876342", "Teo", "Sofia", 4322, new Date),
    Transaction("543265", "George", "Emmanouil", 542311, new Date),
    Transaction("876523", "Emmanouil", "George", 4421, new Date),
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

```

### Bidirectional Flow 

Here is an example of bidirectional flow using a simple encrypt/decrypt problem. To create
bidirectional flow, we use the `BidiShape` in which we define the shapes. Then we interact 
with these shapes using the `.inX`, `.outX` functions.

```scala

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

```


### Cycle Graphs

GraphDSL allows us to create a cycle graph, such as:

```scala
val accelerator = GraphDSL.create() { implicit builder =>
  val sourceShape = builder.add(Source(1 to 10))
  val mergerShape = builder.add(Merge[Int](2))
  val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"$x")
      x + 1
  })

  sourceShape ~> mergerShape ~> incrementerShape
              mergerShape <~ incrementerShape
  ClosedShape
}

RunnableGraph.fromGraph(accelerator).run()
```

However, such graph will not work, as we always pressure the graph with new elements, leading to backpresure and hence to the stop of the flow. This is very common in cycle graphs and it is known as **Graph cycle dead lock**. 

A way to avoid this is to use `MergePreferred` instead of `Merge` prefers an element from its
preferred port and always consume from there. Another way is to configure backpresure in such a way in order to avoid stoping the flow, for instance using `dropHead`





