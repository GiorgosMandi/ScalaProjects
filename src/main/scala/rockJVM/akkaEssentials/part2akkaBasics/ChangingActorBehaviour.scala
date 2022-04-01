package rockJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import rockJVM.akkaEssentials.part2akkaBasics.ChangingActorBehaviour.Mom.MomStart

object ChangingActorBehaviour extends App {

    object FuzzyKid {
        case object KidAccept
        case object KidReject
        val HAPPY = "happy"
        val SAD = "sad"
    }
    class FuzzyKid extends Actor {
        import FuzzyKid._
        import Mom._

        // Warning: The state is mutable which we would rather not have
        var state: String = HAPPY

        override def receive: Receive = {
            case Food(VEGETABLES) => state = SAD
            case Food(CHOCOLATE) => state = HAPPY
            case Ask(_) =>
                if (state==HAPPY) sender() ! KidAccept
                else sender() ! KidReject
        }
    }

    class StatelessFuzzyKid extends Actor {
        import FuzzyKid._
        import Mom._

        // assign function happyReceive as default
        override def receive: Receive = happyReceive

        def happyReceive: Receive ={
            case Food(VEGETABLES) => context.become(sadReceive)
            case Food(CHOCOLATE) =>
            case Ask(_) => sender() ! KidAccept
        }

        def sadReceive: Receive ={
            case Food(VEGETABLES) =>
            case Food(CHOCOLATE) => context.become(happyReceive)
            case Ask(_) => sender() ! KidReject
        }
    }


    class StatelessFuzzyKidWithStack extends Actor {
        import FuzzyKid._
        import Mom._

        // assign function happyReceive as default
        override def receive: Receive = happyReceive // if the stack is empty we execute the default

        def happyReceive: Receive ={
            case Food(VEGETABLES) => context.become(sadReceive, discardOld = false) // push sad to stack
            case Food(CHOCOLATE) =>
            case Ask(_) => sender() ! KidAccept
        }

        def sadReceive: Receive ={
            case Food(VEGETABLES) => context.become(sadReceive, discardOld = false)
            case Food(CHOCOLATE) => context.unbecome() // pop from stack
            case Ask(_) => sender() ! KidReject
        }
    }

    object Mom {
        case class MomStart(kidRef: ActorRef)
        case class Food(food: String)
        case class Ask(question: String)
        val VEGETABLES = "veggies"
        val CHOCOLATE = "chocolate"
    }

    class Mom extends Actor {
        import FuzzyKid._
        import Mom._
        override def receive: Receive = {
            case MomStart(kidRef) =>
                kidRef ! Food(VEGETABLES)
                kidRef ! Food(VEGETABLES)
                kidRef ! Ask("Do you want to play")
                kidRef ! Food(CHOCOLATE)
                kidRef ! Ask("Do you want to play")
                kidRef ! Food(CHOCOLATE)
                kidRef ! Ask("Do you want to play")
            case KidAccept => println("My kid is happy")
            case KidReject => println("My kid is sad")
            case _ => println("Unknown")
        }
    }

    val system: ActorSystem = ActorSystem("fuzzyKidSystem")
    val fuzzyKid: ActorRef = system.actorOf(Props[FuzzyKid])
    val mother: ActorRef = system.actorOf(Props[Mom])

    mother ! MomStart(fuzzyKid)

    Thread.sleep(1000)
    println("\n")

    val statelessFuzzyKid: ActorRef = system.actorOf(Props[StatelessFuzzyKid])
    mother ! MomStart(statelessFuzzyKid)

    // Kid receives Food(VEGETABLES) -> change the handler to sadReceive
    // Kid receives Ask(_) -> kid replies with the sadReceiver so it says KidRejects
    Thread.sleep(1000)
    println("\n")

    val statelessFuzzyKidStack: ActorRef = system.actorOf(Props[StatelessFuzzyKidWithStack])
    mother ! MomStart(statelessFuzzyKidStack)


}
