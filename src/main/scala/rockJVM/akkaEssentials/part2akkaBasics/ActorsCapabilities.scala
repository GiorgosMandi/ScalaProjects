package rockJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorsCapabilities extends App{

    class SimpleActor extends Actor {

        // context.self === 'this'
        // context.self === 'self'
        override def receive: Receive = {
            case number: Int => println(s"[$self]: I have received a number: $number")
            case SpecialMessage(content) => println(s"[$self]: I have received a special message: $content")
            case "path" => println(s"[$self]: My path is ${context.self.path}")
            case "self" => println(s"[$self]: My self is ${self}")

            // send message to myself
            case SendMessageToMyself(message) =>
                println(s"[$self]: Sending to myself ${self.path}")
                self ! message

            // say hi to Actor
            case SayHiTo(ref: ActorRef) =>
                println(s"[$self]: Say Hi to ${ref.path}")
                // in `tell` we send ourself as a sender
                // -> (ref ! "Hi")(self)
                ref ! "Hi"

            // reply to sender
            case "Hi" =>
                println(s"[$self]: Replying to ${sender().path}")
                sender() ! "Hello there!"

            case WirelessPhoneMessage(msg, ref) =>
                println(s"[$self]: Received message from ${sender().path} forward to ${ref.path}")
                // forward keeps the original sender of the message
                ref forward msg

            // default
            case message: String => println(s"[$self]: I have received from ${sender().path} message: $message")
        }
    }

    case class SpecialMessage(content: String)

    val system = ActorSystem("actorCapabilitiesDemo")
    val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

    // 1 - message can be of Any type
    // a) messages must be IMMUTABLE
    // b) messages must be SERIALIZABLE
    // -> in practise use case classes/objects
    simpleActor ! "hello, Actor"
    simpleActor ! 42
    simpleActor ! SpecialMessage("this is special")

    // 2 - Actors have information about their context and themselves
    simpleActor ! "path"
    simpleActor ! "self"

    // sleep to run previous messages
    Thread.sleep(1000)
    println()

    // send message to yourself
    case class SendMessageToMyself(message: String)
    simpleActor ! SendMessageToMyself("message to myself")

    // 3 - Actors can REPLY to messages
    val alice = system.actorOf(Props[SimpleActor], "alice")
    val bob = system.actorOf(Props[SimpleActor], "bob")
    case class SayHiTo(ref: ActorRef)
    alice ! SayHiTo(bob)

    // 4 - dead letters
    // in `alice ! 42` the sender is the noSender which is null
    // -> so what happens if we try to reply to noSender
    alice ! "Hi"
    // we reply to an Actor named DeadLetter which is a fake Actor of Akka that receives all messages that are not send to anyone
    // -> the garbage pool of Akka messages

    // sleep to run previous messages
    Thread.sleep(1000)
    println()

    // 5 - forward messages
    // A -> B -> C
    // forwarding: sending message with the ORIGINAL sender
    case class WirelessPhoneMessage(message: String, ref: ActorRef)
    alice ! WirelessPhoneMessage("Hello", bob)

    Thread.sleep(1000)
    println()

}
