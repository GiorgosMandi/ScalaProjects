package rockJVM.akkaEssentials.part5infra

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, Props, Timers}

import scala.concurrent.duration._
import scala.language.postfixOps

object TimersSchedulers extends App{

    class SimpleActor extends Actor with ActorLogging{
        override def receive: Receive = {
            case message => log.info(message.toString)
        }
    }

    val system: ActorSystem = ActorSystem("TimersSchedulers")
    val simpleActor: ActorRef = system.actorOf(Props[SimpleActor])

    // schedule job to run once after 1 second
    system.scheduler.scheduleOnce(1 second){
        simpleActor ! "Reminder"
    }(system.dispatcher)

    // schedule job to run repeatedly every 2 seconds, after 1 second
    val routine = system.scheduler.schedule(1 second, 2 second){
        simpleActor ! "HeartBeat"
    }(system.dispatcher)

    // cancel repeated scheduler
    system.scheduler.scheduleOnce(5 second){
        routine.cancel()
    }(system.dispatcher)

    Thread.sleep(10000)
    println("\n\n\n")


    /**
     * Staying alive Actor
     *  - if the actor receives a message (anything) you have 1 second to get another
     *  - if the time window expires, the actor stops itself
     *  - otherwise it resets the timer
     */

    class SelfClosingActor extends Actor with ActorLogging {
        case object Timeout
        var routine: Cancellable = createTimeoutWindow
        def createTimeoutWindow: Cancellable = {
            context.system.scheduler.scheduleOnce(1 second) {
                self ! Timeout
            }(context.system.dispatcher)
        }
        override def receive: Receive = {
            case Timeout =>
                log.info("Timed out, stopping myself")
                context.stop(self)
            case _ =>
                log.info("Received message, I reset timer")
                routine.cancel()
                routine = createTimeoutWindow
        }
    }

    val selfClosingActor = system.actorOf(Props[SelfClosingActor], "selfClosingActor")

    // schedule to run every 500ms
    val scheduledMessenger = system.scheduler.schedule(0 second, 500 millis){
        selfClosingActor ! "reset"
    }(system.dispatcher)

    // cancel it after 6 seconds
    system.scheduler.scheduleOnce(6 second) {
        scheduledMessenger.cancel()
    }(system.dispatcher)

    Thread.sleep(10000)
    println("\n\n\n")

    /**
     * Timer
     */

    case object TimerKey
    case object Start
    case object Stop
    case object Reminder
    case object Pause
    class TimerBasedHeartbeatActor extends Actor with ActorLogging with Timers {
        // send message to myself after 500ms
        timers.startSingleTimer(TimerKey, Start, 500 millis)

        override def receive: Receive = {
            case Start =>
                log.info("Bootstrapping")
                // send repeatedly message Reminder to myself every 1s
                timers.startPeriodicTimer(TimerKey, Reminder, 1 second)
            case Reminder =>
                log.info("I am alive")
            case Pause =>
                log.info("I am stopping timer")
                // Stop specific Timer
                timers.cancel(TimerKey)
            case Stop =>
                log.info("I am stopping")
                context.stop(self)
        }
    }
    val timerActor = system.actorOf(Props[TimerBasedHeartbeatActor], "timerActor")

    system.scheduler.scheduleOnce(6 second) {
        timerActor ! Stop
    }(system.dispatcher)

    system.scheduler.scheduleOnce(3 second) {
        timerActor ! Pause
    }(system.dispatcher)

}
