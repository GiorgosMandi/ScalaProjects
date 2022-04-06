package rockJVM.akkaEssentials.part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, PoisonPill, Props}
import akka.dispatch.{ControlMessage, PriorityGenerator, UnboundedPriorityMailbox}
import com.typesafe.config.{Config, ConfigFactory}

object Mailbox extends App{

    val system = ActorSystem("MailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

    class SimpleActor extends Actor with ActorLogging{
        override def receive: Receive = {
            case message => log.info(message.toString)
        }
    }

    /**
     * Interesting case #1  - custom priority mailbox
     * P0 -> most important
     * P1 -> less important
     * P2 -> less important
     * P3 -> least important
     */

    // Step 1 - make mailbox configuration
    class SupportTicketPriorityMailbox(settings: ActorSystem.Settings, config: Config)
      extends UnboundedPriorityMailbox(PriorityGenerator{
          case message: String if message.startsWith("[P0]") => 0
          case message: String if message.startsWith("[P1]") => 1
          case message: String if message.startsWith("[P2]") => 2
          case message: String if message.startsWith("[P3]") => 3
          case _ => 4
      })

    // Step 2 - make it known to the configuration
    // Step 3 - attach the dispatcher to an actor
    val supportTickerLogger = system.actorOf(Props[SimpleActor].withDispatcher("support-ticket-dispatcher"), "supportTickerLogger")
    supportTickerLogger ! PoisonPill // even this is postponed
    supportTickerLogger ! "[P3] this would be nice to have"
    supportTickerLogger ! "[P0] this needs to be solved NOW!"
    supportTickerLogger ! "[P1] do this when you have the time"

    /**
     * Interesting case 2 - control-aware mailbox
     * we will use UnboundedControlAwareMailBox
     */

    // step 1 - mark important messages as control messages
    case object ManagementTicket extends ControlMessage

    // step 2 - configure who gets the mailbox
    // -make the actor attached to mailbox

    val controlAwareActor = system.actorOf(Props[SimpleActor].withMailbox("control-mailbox"), "controlAwareActor")
    controlAwareActor ! "[P3] this would be nice to have"
    controlAwareActor ! "[P0] this needs to be solved NOW!"
    controlAwareActor ! "[P1] do this when you have the time"
    controlAwareActor ! ManagementTicket // highest priority

}
