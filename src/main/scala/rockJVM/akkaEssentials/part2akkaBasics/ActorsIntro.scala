package rockJVM.akkaEssentials.part2akkaBasics

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorsIntro extends App {

    // 1) instantiate actor system
    val actorsSystem = ActorSystem("firstActorSystem")
    println(actorsSystem.name)

    // 2) create actor
    class WordCounterActor extends Actor {

        // internal data
        var totalWords: Int = 0

        // behaviour
        // Receive is alias for PartialFunction[Any, Unit]
        override def receive: Receive = {
            case message: String =>
                println(s"[WordCounter] I have receive: ${message}")
                totalWords += message.split("\\s").length
            case msg => println(s"[WordCounter]: I cant understand ${msg.toString}")
        }
    }

    // 3) instantiate actor - each actor is uniquely identified by each name
    val wordCounter: ActorRef = actorsSystem.actorOf(Props[WordCounterActor], "WordCounter")
    val anotherWordCounter: ActorRef = actorsSystem.actorOf(Props[WordCounterActor], "anotherWordCounter")


    // 4) communicate with actor via  - asynchronous
    wordCounter ! "1) I am learning Akka and it is god damn good" // ! is also known as `tell`
    anotherWordCounter ! "2) Akka is great"
    wordCounter ! "3) Notice that Akka communication is asynchronous"
    anotherWordCounter ! "4) Awesome"

    // 5) instantiate actor with parameters
    class Person(name: String) extends Actor{
        override def receive: Receive = {
            case "hi" => println(s"$name: Hi, my name is $name")
            case "bye" => println(s"$name: I salute you")
            case msg => println(s"$name: $msg")
        }
    }

    // to pass parameter name, we use a companion object that instantiate the Actor with constructor argument
    object Person {
        def props(name: String): Props = Props(new Person(name))
    }

    val george: ActorRef = actorsSystem.actorOf(Person.props("George"), "George")
    // this can also be written as
    val irene: ActorRef = actorsSystem.actorOf(Props(new Person("Irene")), "Irene")

    george ! "hi"
    irene ! "hi"
    george ! "I am tired"
    irene ! "I am not tired"
    george ! "bye"
    irene ! "bye"


}
