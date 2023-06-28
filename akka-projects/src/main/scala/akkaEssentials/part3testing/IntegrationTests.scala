package rockTheJVM.akkaEssentials.part3testing

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{EventFilter, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

// define new akka configuration from src/main/resource/application.conf
class IntegrationTests extends TestKit(ActorSystem("InterceptingLogMessages", ConfigFactory.load().getConfig("interceptingLogMessages")))
  with ImplicitSender
  with AnyWordSpecLike
  with BeforeAndAfterAll {

    import IntegrationTests._

    override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

    "A checkout flow" should {
        val item = "Rock the JVM Akka Essentials"
        val card = "123-492-381-3489"
        val invalidCard = "000-000-000-0000"

        "correctly log dispatch in order" in {
            EventFilter.info(pattern = s"Order [0-9]+ for item $item has been dispatched", occurrences = 1) intercept {
                val checkoutRef = system.actorOf(Props[CheckOutActor])
                checkoutRef ! Checkout(item, card)
            }
        }
        "freak out when card is rejected" in {
            EventFilter[RuntimeException](occurrences = 1) intercept {
                val checkoutRef = system.actorOf(Props[CheckOutActor])
                checkoutRef ! Checkout(item, invalidCard)
            }
        }
    }
}

object IntegrationTests {

    case class Checkout(item: String, card: String)

    case class AuthorizedCard(card: String)

    case object PaymentAccepted

    case object PaymentRejected

    case class DispatchOrder(item: String)

    case object OrderConfirmed

    class CheckOutActor extends Actor {
        val paymentManager: ActorRef = context.actorOf(Props[PaymentManager])
        val fulfillmentManager: ActorRef = context.actorOf(Props[FulfillmentManager])

        override def receive: Receive = awaitingCheckout

        def awaitingCheckout: Receive = {
            case Checkout(item, card) =>
                paymentManager ! AuthorizedCard(card)
                context.become(pendingPayment(item))
        }

        def pendingPayment(item: String): Receive = {
            case PaymentAccepted =>
                fulfillmentManager ! DispatchOrder(item)
                context.become(pendingFulfillment)
            case PaymentRejected => throw new RuntimeException("I cannot accept this card")
        }


        def pendingFulfillment: Receive = {
            case OrderConfirmed => context.become(awaitingCheckout)
        }
    }

    class PaymentManager extends Actor {
        override def receive: Receive = {
            case AuthorizedCard(card) =>
                if (!card.startsWith("0")) sender() ! PaymentAccepted
                else sender() ! PaymentRejected
        }
    }

    class FulfillmentManager extends Actor with ActorLogging {

        override def receive: Receive = orderWithID(42)

        def orderWithID(id: Int): Receive = {
            case DispatchOrder(item) =>
                val newId = id + 1
                log.info(s"Order $newId for item $item has been dispatched")
                sender() ! OrderConfirmed
                context.become(orderWithID(newId))
        }
    }
}


