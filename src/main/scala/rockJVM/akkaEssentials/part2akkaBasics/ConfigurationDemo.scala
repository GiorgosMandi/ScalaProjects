package rockJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object ConfigurationDemo extends App {

    /**Note:
     *  By default Akka loads the configuration file located
     *  in `main/resources/application.conf`  the `akka` element.
     *  To override default, create a new element and load it
     *  using ConfigFactory
     */
    class SimpleLoggingActor extends Actor with ActorLogging {
        override def receive: Receive = {
            case msg: String => log.info(msg)
        }
    }

    val configString: String ="""
          |akka {
          |     loglevel = "DEBUG"
          |     }
          |""".stripMargin

    val config = ConfigFactory.parseString(configString)
    val system = ActorSystem("systemWithDebugLogLevel", ConfigFactory.load(config))

    val logActor = system.actorOf(Props[SimpleLoggingActor])
    logActor ! "this is a logging message"

}
