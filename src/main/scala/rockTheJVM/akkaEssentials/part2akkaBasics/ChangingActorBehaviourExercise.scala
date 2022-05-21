package rockTheJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ChangingActorBehaviourExercise extends App{

    /**
     * 1. - Recreate CounterActor with context.become with NO MUTABLE STATE
     */
    case object Increment
    case object Decrement
    case object Print
    class CounterActor extends Actor{
        override def receive: Receive = countReceive(0)
        // instead of changing the state we change the context with a new state
        def countReceive(currentCount: Int): Receive ={
            case Increment => context.become(countReceive(currentCount+1))
            case Decrement => context.become(countReceive(currentCount-1))
            case Print => println(s"[${self.path.name}]: Count is $currentCount")
        }
    }

    val system = ActorSystem("ActorSystem")
    val counterActor = system.actorOf(Props[CounterActor])

    counterActor ! Increment
    counterActor ! Increment
    counterActor ! Decrement
    counterActor ! Increment
    counterActor ! Print
    counterActor ! Decrement
    counterActor ! Print

    Thread.sleep(1000)
    println("\n")



    /**
     * If send Vote("X") to citizen A it means A voted for X
     *
     * Citizen receives a vote exactly once and after that
     * it will transform itself in a state of having voted with
     * a candidate
     *
     * When a citizen receives a VoteStatusRequest replies with a VoteStatusReply
     * containing an Option Sting
     *
     */
    case class Vote(candidate: String)
    case object VoteStatusRequest
    case class VoteStatusReply(candidateOpt: Option[String])

    class Citizen extends Actor{
        override def receive: Receive ={
            case Vote(c) => context.become(voted(c))
            case VoteStatusRequest => sender() ! VoteStatusReply(None)
        }
        def voted(candidate: String): Receive ={
            case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate) )
        }
    }



    val alice = system.actorOf(Props[Citizen], "Alice")
    val charles = system.actorOf(Props[Citizen], "Charles")
    val bob = system.actorOf(Props[Citizen], "Bob")
    val daniel = system.actorOf(Props[Citizen], "Daniel")
    val george = system.actorOf(Props[Citizen], "George")

    alice ! Vote("Martin")
    bob ! Vote("Jonas")
    charles ! Vote("Roland")
    daniel ! Vote("Roland")

    /**
     * Send messages to Citizens asking the who they have
     * voted for
     *
     * when a voteAggregator receives a AggregateVotes message,
     * it will ask each citizen a VoteStatusRequest
     */
    case class AggregateVotes(citizens: Set[ActorRef])
    class VoteAggregator extends Actor {
        override def receive: Receive = awaitCommands

        def awaitCommands: Receive = {
            case AggregateVotes(candidates) =>
                context.become(awaitingStatuses(Map[String, Int](), candidates))
                candidates.foreach(c => c ! VoteStatusRequest)
        }

        def awaitingStatuses(currentStatuses: Map[String, Int], stillWaiting: Set[ActorRef]): Receive = {
            case VoteStatusReply(None) =>
                // Warning: In case a user never votes it results to infinite loop
                sender() ! VoteStatusRequest
            case VoteStatusReply(Some(candidate)) =>
                // we remove the sender from the still waiting
                val newStillWaiting = stillWaiting - sender()
                val currentVoteOfCandidate = currentStatuses.getOrElse(candidate, 0)
                val newStats = currentStatuses + (candidate -> (currentVoteOfCandidate+1))
                if (newStillWaiting.isEmpty)
                    println(s"My current votes: $newStats")
                else
                    context.become(awaitingStatuses(newStats, newStillWaiting))
        }
    }

    val voteAggregator = system.actorOf(Props[VoteAggregator])
    voteAggregator ! AggregateVotes(Set(alice, bob, charles, daniel))

    /*
        Martin -> 1
        Jonas -> 1
        Roland -> 2
     */

}
