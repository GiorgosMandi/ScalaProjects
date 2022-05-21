package rockTheJVM.akkaEssentials.part1scalaBasics

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MultiThreadingRecap extends App {

    /** Creating threads on JVM */
    val aThread = new Thread(() => println("Im running in parallel"))
    aThread.start()
    // wait to finish
    aThread.join()

    // Warning:
    //  the main problem with threads is that they're unpredictable
    //  different runs predict different results!!
    val threadHello = new Thread(() => (1 to 10).foreach(_ => println("Hello")))
    val threadGoodbye = new Thread(() => (1 to 10).foreach(_ => println("Goodbye")))
    threadHello.start()
    threadGoodbye.start()
    threadHello.join()
    threadGoodbye.join()


    class BankAccount(@volatile private var amount:Int){
        // This is not thread safe as two threads can operate concurrently and not sequentially
        // watching the same value. To work properly, the variable `amount` should be ATOMIC
        // -> volatile i.e. not two threads can read/write this var concurrently
        def withdraw(money: Int): Unit = amount -= money

        // using synchronized, it prevents the evaluation of this expression concurrently
        def threadSafeWithdraw(money: Int): Unit = this.synchronized(
            amount -= money
        )
    }

    /** Inter threads Communications */
    import scala.concurrent.ExecutionContext.Implicits._
    val future = Future{
        // runs on a different thread
        42
    }
    future.onComplete{
        case Success(42) => println("I found the meaning of life")
        case Failure(_) => println("Something went wrong")
    }
    val newFuture = future.map(_ + 1)
    val filteredFuture = future.filter(_%2==0)
    val trueFuture = for {
        meaningOfLife <- future
        filteredMeaningOfLife <- filteredFuture
        trueMeaningOfLife <- newFuture
    } yield meaningOfLife+filteredMeaningOfLife+trueMeaningOfLife




}
