package sparkStreaming.part2structuredstreaming

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Column, DataFrame, SparkSession}

object StreamingAggregations {

    val spark = SparkSession.builder()
      .appName("Our first Stream")
      .master("local[2]")
      .getOrCreate()

    def streamingCount() = {
        val lines: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load()

        val linesCount = lines.selectExpr("count(*) as linesCount")

        linesCount.writeStream
          .format("console")
          .outputMode("complete") // append and update are not supported on aggregations without watermark
          .start()
          .awaitTermination()
    }

    def numericalAggregations(aggFunction: Column => Column): Unit = {
        val lines: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load()

        val numbers = lines.select(col("value").cast("integer").as("number"))
        val aggregationDF = numbers.select(aggFunction(col("number")))

        aggregationDF.writeStream
          .format("console")
          .outputMode("complete")
          .start()
          .awaitTermination()
    }

    def groupNames() = {
        val lines: DataFrame = spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load()

        // counting the occurrences of the "name" value
        val names = lines.select(col("value").as("name"))
          .groupBy(col("name")) // RelationalGroupDataset
          .count()

        names.writeStream
          .format("console")
          .outputMode("complete")
          .start()
          .awaitTermination()
    }

    def main(args: Array[String]): Unit = {
//        numericalAggregations(sum)
        groupNames()
    }
}
