package rockTheJVM.akkaEssentials.part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Terminated}
import akka.routing._
import com.typesafe.config.ConfigFactory

object Routers extends App {

    /**
     * #1 - Manual Router
     * */

    println(s"#1 - Manual Router\n")
    class Master extends Actor {
        // step 1 - create routees
        // 5 slaves based off Slave actors
        val slaves = for(i <- 1 to 5) yield {
            val slave = context.system.actorOf(Props[Slave], s"Slave_$i")
            context.watch(slave)
            ActorRefRoutee(slave)
        }

        // step 2 - define routers
        private var routers = Router(RoundRobinRoutingLogic(), slaves)

        override def receive: Receive = {
            case Terminated(ref) =>
                // step 4 - handle the termination/lifecycle of the routes
                routers = routers.removeRoutee(ref)
                val newSlave = context.actorOf(Props[Slave])
                routers = routers.addRoutee(newSlave)
            case message =>
                // step 3 - route messages
                routers.route(message, sender())
        }
    }

    class Slave extends Actor with ActorLogging {
        override def receive: Receive = {
            case message => log.info(message.toString)
        }
    }


    val system = ActorSystem("RoutersSystem")
    val master = system.actorOf(Props[Master], "Master")
    for (i <- 1 to 10)
        master ! s"[$i]: hello from world"

    Thread.sleep(2000)
    println(s"\n\n\n#2 - Manual Router\n")
    /**
     * #2 - A Router with its onw children
     * POOL Router
     * */

    val masterPool = system.actorOf(RoundRobinPool(nrOfInstances = 5).props(Props[Slave]))
    for (i <- 1 to 10)
        master ! s"[$i]: hello from world"

    system.terminate()
    Thread.sleep(2000)
    println(s"\n\n\n#2.5 - Pool Router from Configuration\n")
    /**
     * #2.5 - A Router with its onw children
     * POOL Router from config
     */
    val system2 = ActorSystem("RoutersSystemByConf", ConfigFactory.load().getConfig("routersDemo"))
    val poolMaster2 = system2.actorOf(FromConfig.props(Props[Slave]), "poolMaster2")
    for (i <- 1 to 10)
        poolMaster2 ! s"[$i]: hello from world"

    system2.terminate()
    Thread.sleep(2000)
    println(s"\n\n\n#3 - Group Router\n")
    /**
     * #3 - Router with actors created elsewhere
     * GROUP Router
     */
    val system3 = ActorSystem("RoutersSystem3")
    val slavesList = (1 to 5).map(i => system3.actorOf(Props[Slave], s"Slave_$i"))

    // get slaves paths
    val slavesPaths = slavesList.map(slaveRef => slaveRef.path.toString)
    val groupMaster = system3.actorOf(RoundRobinGroup(slavesPaths).props())
    for (i <- 1 to 10)
        groupMaster ! s"[$i]: hello from world"

    // Note: similarly with 2.5, you can create groups using configuration

    /**
     * Special Messages
     */
    groupMaster ! Broadcast("Hello from Group master")



}
