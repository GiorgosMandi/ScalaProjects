package sparkStreaming.part2structuredstreaming

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, SparkSession}

object StreamingJoin {

    val spark = SparkSession.builder()
      .appName("Our first Stream")
      .master("local[2]")
      .getOrCreate()


    // Static DataFrames
    val guitarPlayers = spark
      .read.option("inferSchema", true)
      .json("/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/guitarPlayers")

    val guitars = spark
      .read.option("inferSchema", true)
      .json("/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/guitars")

    val bands = spark
      .read.option("inferSchema", true)
      .json("/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/bands")

    val bandsSchema = bands.schema
    val joinCondition = guitarPlayers.col("band") === bands.col("id")
    val guitaristsBand = guitarPlayers.join(bands, joinCondition, "inner")

    def joinStreamWithStatic(): Unit = {
        // Stream Dataframe
        val streamBandsDF: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load() // a DF with a single column "value" of type String
          .select(from_json(col("value"), bandsSchema).as("band")) // composite band column
          .selectExpr("band.id as id", "band.name as name", "band.hometown as hometown", "band.year as year")

        // Joins happen per batch
        //   restricted joins:
        // -  in stream DF - static DF join, the RIGHT outer joins/full outer joins/right_semi are not permitted
        // - in static DF - streamed DF join, the LEFT outer joins/full/left_semi are not permitted
        // -> sparks need to keep track of all the incoming data but spark does not allow the accumulation of unbounded data
        val streamedBandGuitaristsDF = streamBandsDF.join(guitarPlayers, guitarPlayers.col("band") === streamBandsDF.col("id"), "inner")

        streamedBandGuitaristsDF.writeStream
          .format("console")
          .outputMode("append") // append and update are not supported on aggregations without watermark
          .start()
          .awaitTermination()
    }

    def joinStreamWithStream(): Unit = {
        // Stream Dataframe
        val streamBandsDF: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load() // a DF with a single column "value" of type String
          .select(from_json(col("value"), bandsSchema).as("band")) // composite band column
          .selectExpr("band.id as id", "band.name as name", "band.hometown as hometown", "band.year as year")

        // Stream Dataframe
        val streamGuitaristsDF: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12346")
          .load() // a DF with a single column "value" of type String
          .select(from_json(col("value"), guitarPlayers.schema).as("guitarist")) // composite band column
          .selectExpr("guitarist.id as id", "guitarist.name as name", "guitarist.guitars as guitars", "guitarist.band as band")

        val streamedJoin = streamBandsDF.join(streamGuitaristsDF, streamGuitaristsDF.col("band") === streamBandsDF.col("id"), "inner")

        // - inner joins are supported
        // - left/right join are supported ONLY with watermarks
        // - full outer join are not supported
        streamedJoin.writeStream
          .format("console")
          .outputMode("append") // append and update are not supported on aggregations without watermark
          .start()
          .awaitTermination()
    }


    def main(args: Array[String]): Unit = {
        joinStreamWithStream()
    }

}
