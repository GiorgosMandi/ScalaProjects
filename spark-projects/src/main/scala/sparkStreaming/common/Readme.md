# Spark - Streaming

## General Information

## Stream Processing: 

- Include new data to compute results
- No definitive end of incoming data
- Batch processing 
  - continues processing: instantly processed / lower latency
  - micro batches: process after gathering a few data points / higher throughput
  - Spark streaming operates on micro-batches
  - continues processing is an experimental feature

## Pros
 - Much lower latency
 - Greater performance/efficency

## Difficulties
- Maintaining state and order of incoming data
- exactly once processing in context of machine failures
- responding at low latency
- updating business logic at runtime

_**Note**_: 
 - DStreams: low level API 
 - Structured Streaming: high level API

## Structured Streaming

 _**Output Modes**_:
- append: only add new records
- update: modify records in place / if no aggregation is equal to append
- complete: rewrite everything 

Triggers: when the data is written:
- default: write as soon as the micro-batch is processed
- once: write the current micro-batch and stop
- processing time: look for new data at fixed intervals
- continues: (experimental): every processing time, create a batch with whatever new instances there are (otherwise the batch will be empty)

## Aggregations

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

## Joins

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