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