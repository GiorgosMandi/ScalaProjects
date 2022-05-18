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
