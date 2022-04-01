package rockJVM.akkaEssentials.part1scalaBasics

import scala.concurrent.Future

object ThreadsLimitations extends App{
    /** Issues of JVM threads */


    /**
     * 1 - Thread encapsulation is only valid in the Single Thread model
     */

    case class BankAccount( private var amount:Int){
        override def toString: String = "" + amount
        def withdraw(money: Int): Unit = amount -= money
        def deposit(money: Int): Unit = amount += money
        def getAmount: Int = amount
    }

    // a thousand threads that reduce 1000 from amount
    // and a thousand threads tha increases amount by 1000
//    val bankAccount = BankAccount(200)
//    for (_ <- 1 to 100)
//        new Thread(() => bankAccount.withdraw(1)).start()
//    for (_ <- 1 to 100)
//        new Thread(() => bankAccount.deposit(1)).start()
//
//    println(bankAccount.getAmount)

    //Warning: OOP encapsulation is broken in multithreading env
    // OOP involves synchronization -> Locks to the rescue| deadlocks, livelocks

    /**
     * 2 -Delegating something to a thread is PAIN
     */

    // you have a running thread and you want to pass a runnable to that thread
    var task: Runnable = null
    val runningThread: Thread = new Thread(() =>
        while(true){
            while(task == null){
                runningThread.synchronized {
                    println("[background] Waiting for a task...")
                    runningThread.wait()
                }
            }
            runningThread.synchronized{
                println("[background]I have a task")
                task.run()
                task = null
            }
        })

    def delegateToBackgroundThread(r: Runnable): Unit ={
        if (task == null) task = r
        runningThread.synchronized(runningThread.notify())
    }

    runningThread.start()
    Thread.sleep(1000)
    delegateToBackgroundThread(() => println("42"))
    Thread.sleep(2000)
    delegateToBackgroundThread(() => println("this should run in background"))


    /**
     * More issues:
     *  other signals?
     *  multiple background tasks and threads (how do you delegate)
     *  who gave the signal?
     *  what if I crash?
     *
     *   ->
     *   we need a data structure which
     *      -safely receives messages
     *      -can identify senders
     *      -can guard against errors
     */

    /**
     * 3) Tracing and dealing with errors
     */

    import scala.concurrent.ExecutionContext.Implicits.global

    // very hard to debug when a exception occurs in a thread killing the whole JVM
    // ->> OOP is not encapsulated
    val futures = (0 to 9)
      .map(i => 1000*i until 1000*(i+1))
      .map(range => Future{
          if (range.contains(5486)) throw new RuntimeException("Invalid number")
          range.sum
      })
    val sumFuture = Future.reduceLeft(futures)(_ + _)
    sumFuture.onComplete(println)
}
