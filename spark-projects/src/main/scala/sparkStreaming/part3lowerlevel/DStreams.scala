package sparkStreaming.part3lowerlevel

import org.apache.spark.sql.SparkSession
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.{Seconds, StreamingContext}

import java.sql.Date
import java.text.SimpleDateFormat
import sparkStreaming.common._

import java.io.{File, FileWriter}
object DStreams {

    val spark = SparkSession.builder()
      .appName("DStreams")
      .master("local[2]")
      .getOrCreate()

    /*
    Spark Streaming Context: the entry point to the DStream API
    - spark context
    - a duration -> batch interval
     */
    val ssc = new StreamingContext(sparkContext = spark.sparkContext, batchDuration = Seconds(1))

    /*
    - define input sources by creating DStreams
    - define transformation on DStreams
    - call an action on DStreams
    - start computation with ssc.start() -> no more computation can be added
    - await termination or stop the computation -> you cannot restart the ssc
     */

    def readFromSocket(): Unit = {
        // DStream definition
        val socketStream: DStream[String] = ssc.socketTextStream("localhost", 12345)
        // transformation = lazy
        val wordsStream: DStream[String] = socketStream.flatMap(line => line.split(" "))
        // action
        wordsStream.print()

        // start computation
        ssc.start()
        ssc.awaitTermination()
    }

    def createFile() ={
        new Thread(() => {
            Thread.sleep(5000)
            val path = "/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/stocks"
            val dir = new File(path)
            val nFiles = dir.listFiles().length
            val newFile = new File(s"$path/newStocks$nFiles.csv")
            newFile.createNewFile()

            val writer = new FileWriter(newFile)
            writer.write(
                """AMZN,Aug 1 2000,41.5
                  |AMZN,Sep 1 2000,38.44
                  |AMZN,Oct 1 2000,36.62
                  |AMZN,Nov 1 2000,24.69
                  |AMZN,Dec 1 2000,15.56
                  |AMZN,Jan 1 2001,17.31
                  |AMZN,Feb 1 2001,10.19
                  |AMZN,Mar 1 2001,10.23
                  |AMZN,Apr 1 2001,15.78
                  |AMZN,May 1 2001,16.69
                  |AMZN,Jun 1 2001,14.15
                  |AMZN,Jul 1 2001,12.49
                  |AMZN,Aug 1 2001,8.94
                  |AMZN,Sep 1 2001,5.97""".stripMargin)
            writer.close()
        }).start()
    }
    def readFromFile(): Unit = {
        createFile()
        val stocksFilePath = "/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/stocks"

        /*
            textFileStream - monitors a directory  for NEW FILES
         */
        val textStream: DStream[String] = ssc.textFileStream(stocksFilePath)

        val dateFormat = new SimpleDateFormat("MMM d yyyy")
        val stocksS: DStream[Stock] = textStream.map { line =>
            val tokens = line.split(",")
            val company = tokens(0)
            val date = new Date(dateFormat.parse(tokens(1)).getTime)
            val price = tokens(2).toDouble
            Stock(company, date, price)
        }

        // each folder is a batch (RDD) and each file is a partition
        stocksS.saveAsTextFiles("/home/gmandi/Documents/myProjects/Educational/ScalaProjects/spark-projects/src/main/resources/data-streaming/words")

        ssc.start()
        ssc.awaitTermination()

    }


    def main(args: Array[String]): Unit = {
        readFromFile()
    }
}
