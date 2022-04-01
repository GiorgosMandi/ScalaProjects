package rockJVM.akkaEssentials

import akka.actor.ActorSystem

object Playground extends App {

    val actorSystem: ActorSystem = ActorSystem("Hello-World")
    println(actorSystem.name)
}
