package rockTheJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import rockTheJVM.akkaEssentials.part2akkaBasics.ChildActorsExercise.WordCounterMaster.Initialize

object ChildActorsExercise extends App {

    //Note: Distributed Word counting
    //  Follow the following steps
    //  i) Create WordCounterMaster
    //  ii) send Initialize(10) to WordCounterMaster
    //  iii) send "Akka is awesome" to WordCounterMaster
    //      wcm -> WordCountTask to a child
    //      wcm <- WordCountReply from child
    //  iv) master replies with the number of words
    object WordCounterMaster{
        // create n children1
        case class Initialize(n: Int)
        // count words task
        case class WordCountTask(text: String)
        case class WordCountReply(count: Int)
    }
    class WordCounterMaster extends Actor{
        import WordCounterMaster._
        val logger: LoggingAdapter = Logging(context.system, this)
        override def receive: Receive = {
            case Initialize(n) =>
                logger.info(s"Initializing $n children")
                val children: Seq[ActorRef] = (1 to n).map(i => context.actorOf(Props[WordCounterWorker], s"WCW_$i"))
                context.become(withChildren(children, 0))
        }

        def withChildren(children: Seq[ActorRef], childIndex: Int): Receive ={
            case text: String =>
                val childRef = children(childIndex)
                logger.info(s"""Forward task to ${self.path} """)
                childRef forward WordCountTask(text)
                val newChildIndex = (childIndex+1) % children.length
                context.become(withChildren(children, newChildIndex))
            case WordCountReply(count) => sender() ! count
        }
    }

    class WordCounterWorker extends Actor{
        import WordCounterMaster._
        val logger: LoggingAdapter = Logging(context.system, this)
        override def receive: Receive = {
            case WordCountTask(text) =>
                logger.info(s"""Received Task with text: \"$text\" """)
                val count = text.split("\\s").length
                sender() forward WordCountReply(count)
        }
    }

    class WordCounterTester extends Actor with ActorLogging {
        override def receive: Receive = {
            case "go" =>
                val wcm = context.actorOf(Props[WordCounterMaster], "WCM")
                wcm ! Initialize(5)
                val texts = List( "Akka is Awesome 1", "I love scala", "scala is super dope", "yes", "me too")
                texts.foreach(text => wcm ! text)
            case count =>
                log.info(s"I received count: $count")
        }
    }

    val system = ActorSystem("System")
    val tester = system.actorOf(Props[WordCounterTester], "tester")
    tester ! "go"
}
