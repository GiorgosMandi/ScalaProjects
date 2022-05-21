package rockTheJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import rockTheJVM.akkaEssentials.part2akkaBasics.ChildActor.Parent.{CreateChild, TellChild}

object ChildActor extends App {

    object Parent {
        case class CreateChild(name: String)
        case class TellChild(message: String)
    }
    class Parent extends Actor {
        import Parent._
        override def receive: Receive = {
            case CreateChild(name) =>
                println(s"[${self.path}]: Create child $name")
                // create actor RIGHT HERE
                val childRef = context.actorOf(Props[Child], name)
                context.become(withChild(childRef))
        }
        def withChild(childRef: ActorRef): Receive ={
            case TellChild(message) => childRef forward message
        }
    }

    class Child extends Actor {
        override def receive: Receive ={
            case message => println(s"[${self.path}]: I got: $message")
        }
    }

    val system = ActorSystem("ParentChildSystem")
    val parent = system.actorOf(Props[Parent], "parent")
    parent ! CreateChild("child")
    parent ! TellChild("are alive?")

    /** Note:
     *      This way, actor have hierarchies
     *      parent -> daughter -> grandSon
     *              -> son -> grandDaughter
     *      Parent is not a top level actor, Top level actors are the ones called guardians
     *      Guardian-Actors (top-level)
     *      1- /system = system guardian
     *      2- /user = user-level guardian
     *      3- / = root guardian
     *
     */


    /**
     * Actor Selection - find actor by path
     */
    val childSelection = system.actorSelection("/user/parent/child")
    childSelection ! "I found you"

    // Danger: NEVER PASS MUTABLE STATE OR `THIS` PREFERENCE TO CHILD ACTORS
    //   It may break threads encapsulation
}
