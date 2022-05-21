package rockTheJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import rockTheJVM.akkaEssentials.part2akkaBasics.ActorsCapabilitiesExercises.BankAction.{BankActionT, ShowBalance, Deposit, Withdraw,
    TransactionFailure, TransactionSuccess}

import scala.util.Random

/**
 * Exercises
 *  1.Create CounterActor
 *      -Increment
 *      -Decrement
 *      -Print
 *
 *  2. Create a Bank account as an Actor
 *      Receives:
 *          -Deposit an amount
 *          -Withdraw an amount
 *          -Statement
 *      Replies with:
 *          -Success
 *          -Failure
 *
 *      *interact with some other actor
 */
object ActorsCapabilitiesExercises extends App{

    /**
     * 1.Create CounterActor
     *      -Increment
     *      -Decrement
     *      -Print
     */
    case object Increment
    case object Decrement
    case object Print
    class CounterActor extends Actor{
        var count = 0
        override def receive: Receive = {
            case Increment => count += 1
            case Decrement => count -= 1
            case Print => println(s"[${self.path}]: Amount: $count")
            case _ => println(s"[${self.path}]: Unknown value")
        }
    }

    val system = ActorSystem("ActorSystem")
    val counterActor = system.actorOf(Props[CounterActor])

    counterActor ! Increment
    counterActor ! Increment
    counterActor ! Decrement
    counterActor ! Increment
    counterActor ! Print
    counterActor ! Increment
    counterActor ! Decrement
    counterActor ! Increment
    counterActor ! Print
    (1 to 5).foreach(_ => counterActor ! Increment)
    (1 to 5).foreach(_ => counterActor ! Decrement)
    counterActor ! Print


    Thread.sleep(1000)
    println("\n\n")

    /**
     *  *  2. Create a Bank account as an Actor
     *      Receives:
     *          -Deposit an amount
     *          -Withdraw an amount
     *          -Statement
     *      Replies with:
     *          -Success
     *          -Failure
     *
     *      *interact with some other kind of actor
     */

    object BankAction {
        sealed trait BankActionT
        case class Deposit(value: Int) extends BankActionT{
            override def toString: String = s"Deposit $value"
        }
        case class Withdraw(value: Int) extends BankActionT {
            override def toString: String = s"Withdraw $value"
        }
        case class ShowBalance() extends BankActionT

        // possible results of a bank action
        sealed trait TransactionResult
        case class TransactionSuccess(message: String) extends TransactionResult
        case class TransactionFailure(reason: String) extends TransactionResult
    }


    class BankAccount extends Actor {
        var balance = 0
        override def receive: Receive = {
            case Deposit(amount) =>
                val depositResult =
                    if (amount < 0)
                        TransactionFailure(s"Invalid Deposit of $amount")
                    else {
                        balance += amount
                        TransactionSuccess(s"Deposit of $amount was executed successfully")
                    }
                    sender() ! depositResult
            case Withdraw(amount) =>
                val withdrawResult =
                    if (balance < amount) TransactionFailure(s"Invalid Withdraw of $amount")
                    else {
                        balance -= amount
                        TransactionSuccess(s"Withdraw of $amount was executed successfully")
                    }
                sender() ! withdrawResult

            case ShowBalance() =>
                sender() ! TransactionSuccess(s"[${self.path.name}]: Balance is $balance")
        }
    }

    // a person that is paired with his own bank account
    // we interact with the bank account only via its owner
    class Person(accountRef: ActorRef) extends Actor {
        override def receive: Receive = {
            case action: BankActionT => accountRef ! action
            case TransactionSuccess(msg) => println(s"[${accountRef.path.name}]: $msg")
            case TransactionFailure(msg) => println(s"[${accountRef.path.name}]: $msg")
            case _ => println(s"Unresolved message")
        }
    }
    object Person{
        def props(ref: ActorRef): Props= Props(new Person(ref))
    }

    val georgeAccount = system.actorOf(Props[BankAccount], "George-Account")
    val george = system.actorOf(Person.props(georgeAccount), "George")

    val ireneAccount = system.actorOf(Props[BankAccount], "Irene-Account")
    val irene = system.actorOf(Person.props(ireneAccount), "Irene")

    val values = Seq.fill(100)(Random.nextInt(100))
    values.foreach { v =>
        val i = Random.nextInt(50)
        val bankAction: BankActionT = if (i%2==0) Deposit(v) else Withdraw(v)
        val accountRef: ActorRef = i%2 match {
            case 0 => george
            case 1 => irene
        }
        accountRef ! bankAction
    }
    Thread.sleep(1000)
    println("\n\n")

    irene ! ShowBalance()
    george ! ShowBalance()
}
