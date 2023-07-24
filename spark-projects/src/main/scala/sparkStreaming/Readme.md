# Spark - Streaming

## General Information

## Introduction - Stream Processing: 

- Include new data to compute results
- No definitive end of incoming data
- Batch processing 
  - continues processing: instantly processed / lower latency
  - micro batches: process after gathering a few data points / higher throughput
  - Spark streaming operates on micro-batches
  - continues processing is an experimental feature

### Pros
 - Much lower latency
 - Greater performance/efficency

### Difficulties
- Maintaining state and order of incoming data
- exactly once processing in context of machine failures
- responding at low latency
- updating business logic at runtime

_**Note**_: 
 - DStreams: low level API 
 - Structured Streaming: high level API

---
## Section 1 - Structured Streaming

### Streaming DataFrames

 _**Output Modes**_:
- append: only add new records
- update: modify records in place / if no aggregation is equal to append
- complete: rewrite everything 

Triggers: when the data is written:
- default: write as soon as the micro-batch is processed
- once: write the current micro-batch and stop
- processing time: look for new data at fixed intervals
- continues: (experimental): every processing time, create a batch with whatever new instances there are (otherwise the batch will be empty)


#### Aggregations

Comments:
- aggregations work at micro-batches
- the append output mode is not supported without watermarks
- there are no `distinct` aggregations - can't store the whole state of a unbound stream
- some aggregations are not supported, such as sorting or chained aggregations

Examples:
```scala
val numbers = lines.select(col("value").cast("integer").as("number"))
val aggregationDF = numbers.select(sum(col("number")))
```

Grouping Example:
```scala
// counting the occurrences of the "name" value
val names = lines.select(col("value").as("name"))
  .groupBy(col("name")) // RelationalGroupDataset
  .count()
```

#### Joins

1. Join a Stream DF with a Static DF

    In these cases Joins happen per batch. However, there are some **restriction** of the supported types of joins:
   - in stream DF - static DF join -> the RIGHT outer joins/full/right_semi are not permitted
   - in static DF - streamed DF join -> the LEFT outer joins/full/left_semi are not permitted

    This is because, spark to be able to perform these joins, it would need to keep track of all the incoming data.
    Spark does not allow the accumulation and maintain the information of unbounded data in memory.

    ```scala
    val streamedBandGuitaristsDF = streamBandsDF.join(guitarPlayers, guitarPlayers.col("band") === streamBandsDF.col("id"), "inner")
    ```

2. Join a Stream DF with a Stream DF

   In these cases Joins happen per batch. However, there are some **restriction** of the supported types of joins:
   -  inner joins are supported
   - left/right join are supported **ONLY** with watermarks
   - full outer join are **NOT** supported

    ```scala
        val streamedJoin = streamBandsDF.join(streamGuitaristsDF, streamGuitaristsDF.col("band") === streamBandsDF.col("id"), "inner")
    ```
   
### Streaming Dataset

Streaming Datasets are very powerful as they support both functional operators and SQL like queries.
Tradeoff:
 - pros: 
   - type safety
   - expressiveness
 - cons
   - potential performance implications as lambdas cannot be optimized

```scala
import spark.implicits._

val carSDS = spark.readStream
  .format("json")
  .option("dateFormat", "YYYY-mm-dd")
  .schema(carsSchema)
  .load("/PATH/TO/DIR")
  .as[Car]

carSDS.filter(car => car.Horsepower.getOrElse(0L) > 140L)
```
---
## Section 2 - Low level Spark Streaming with DStreams

Discretized Streams (aka DStreams) is a never ending sequence of RDDs. Each micro-batch that Spark Stream will delimit will be an RDD. 
Each batch is formed by all new instances that are coming within a time interval known as **batchInterval**. The number of partitions 
that will be assigned to each Executor is configured by a different time interval known as **blockInterval**. **Both intervals are configurable**

![fibonacci_seq_cycle.png](https://raw.githubusercontent.com/GiorgosMandi/ScalaProjects/main/spark-projects/src/main/resources/images/dstream.png)

To create a DStream, we need to initialize a StreamingContext using the SparkContext and the batch interval. Then, we
define

- the source
- define transformations, which are all lazy and will not be executed without actions
- call action
- start computation with ssc.start() -> no more computation can be added
- await termination or stop the computation -> you cannot restart the ssc 

Here is a Simple example:

```scala
// initialize streaming context
val ssc = new StreamingContext(sparkContext = spark.sparkContext, batchDuration = Seconds(1))
// DStream definition
val socketStream: DStream[String] = ssc.socketTextStream("localhost", 12345)
// transformation = lazy
val wordsStream: DStream[String] = socketStream.flatMap(line => line.split(" "))
// action
wordsStream.print()

// start computation
ssc.start()
ssc.awaitTermination()
```

