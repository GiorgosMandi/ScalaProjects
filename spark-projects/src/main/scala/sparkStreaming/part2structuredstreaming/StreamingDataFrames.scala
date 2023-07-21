package sparkStreaming.part2structuredstreaming

import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.{DataFrame, SparkSession}
import sparkStreaming.common.stocksSchema
import scala.concurrent.duration._
object StreamingDataFrames {

    val spark = SparkSession.builder()
      .appName("Our first Stream")
      .master("local[2]")
      .getOrCreate()

    def readFromSocket() = {
        val lines: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load()

        val shortLines = lines.filter(length(col("value")) < 5)

        println(shortLines.isStreaming)

        val query = shortLines.writeStream
          .format("console")
          .outputMode("append")
          .start()

        query.awaitTermination()
    }

    def readFromFile() = {
        val stockDF = spark.readStream
          .format("csv")
          .option("header", "false")
          .option("dateFormat", "MMM d yyyy")
          .schema(stocksSchema)
          .load("/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/stocks")

        stockDF.writeStream
          .format("console")
          .outputMode("append")
          .start()
          .awaitTermination()
    }

    def demoTriggers() = {
        val lines: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load()

        lines.writeStream
          .format("console")
          .outputMode("append")
          .trigger(
              Trigger.ProcessingTime(2.seconds)   // run the query every two seconds
//            Trigger.Once() // create a single batch from the existing data
//            Trigger.Continuous(2.second) // create a micro-batch every 2 seconds - if no new items, an empty micro-batch will be created
          )
          .start()
          .awaitTermination()
    }


    def main(args: Array[String]): Unit = {
        demoTriggers()
    }

}
