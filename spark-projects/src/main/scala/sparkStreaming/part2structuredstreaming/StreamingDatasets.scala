package sparkStreaming.part2structuredstreaming

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}
import sparkStreaming.common.{Car, carsSchema}

object StreamingDatasets {

    val spark = SparkSession.builder()
      .appName("Our first Stream")
      .master("local[2]")
      .getOrCreate()

    // includes DF -> DS transformer

    import spark.implicits._

    def readCars(): Dataset[Car] = {
        spark.readStream
          .format("socket")
          .option("host", "localhost")
          .option("port", "12345")
          .load()
          .select(from_json(col("value"), carsSchema).as("car")) // composite band column
          .selectExpr("car.*") // DF multiple columns
          .as[Car]
    }

    def showCarNames() = {
        val carsDS = readCars()

        // transformation here
        val carNamesDF: DataFrame = carsDS.select(col("name"))

        //collection transformation maintain type info
        val carNameDS: Dataset[String] = carsDS.map(_.Name)
        carNameDS.writeStream
          .format("console")
          .outputMode("append")
          .start()
          .awaitTermination()
    }

    /*
     * Exercises
     *
     * 1) How many powerful cars there are in the DS (HP > 140)
     * 2) Print the average HP for the entire dataset
     *   - use the `complete` output mode
     *
     * 3) count the cars based on the origin field
     *
     * */
    def getPowerfulCars(): Dataset[Car] = {
        val carSDS = spark.readStream
          .format("json")
          .option("dateFormat", "YYYY-mm-dd")
          .schema(carsSchema)
          .load("/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/cars")
          .as[Car]
        carSDS
          .filter(car => car.Horsepower.getOrElse(0L) > 140L)

    }

    def countPowerfulCars() = {
        val powerfulCars = getPowerfulCars()
          .select(count("*"))

        powerfulCars.writeStream
          .format("console")
          .outputMode("complete")
          .start()
          .awaitTermination()
    }

    def avgPowerfulCars() = {
        val powerfulCars = getPowerfulCars()
          .select(avg("Horsepower"))

        powerfulCars.writeStream
          .format("console")
          .outputMode("complete")
          .start()
          .awaitTermination()
    }

    def countCarsByOrigin() = {

        val carSDS = spark.readStream
          .format("json")
          .option("dateFormat", "YYYY-mm-dd")
          .schema(carsSchema)
          .load("/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/cars")
          .as[Car]
          .groupByKey(car => car.Origin)
          .count()

        carSDS.writeStream
          .format("console")
          .outputMode("complete")
          .start()
          .awaitTermination()
    }


    def main(args: Array[String]): Unit = {
        countCarsByOrigin()
    }

}
