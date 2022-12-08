package rockTheJVM.akkaTyped

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, DispatcherSelector}
import rockTheJVM.akkaTyped.AkkaMessageAdaption.Checkout.{InspectSummary, Summary}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object AkkaMessageAdaption {
    // Note:
    // actors: customer -> Checkout -> ShoppingCart
    //                      *frontend*  *backend*

    object StoreDomain {
        // never use double for money - for illustration purposes
        case class Product(name: String, price: Double)
    }

    object ShoppingCart {

        import StoreDomain._

        sealed trait Request

        case class GetCurrentCart(cartId: String, replyTo: ActorRef[Response]) extends Request

        sealed trait Response

        case class CurrentCart(cartId: String, items: List[Product]) extends Response

        val db: Map[String, List[Product]] = Map {
            "123-abc-456" -> List(Product("iPhone", 20202020), Product("selfie-stick", 30))
        }

        def dummy(): Behavior[Request] = Behaviors.receiveMessage {
            case GetCurrentCart(cartId, replyTo) =>
                replyTo ! CurrentCart(cartId, db(cartId))
                Behaviors.same
        }

    }

    object Checkout {

        import ShoppingCart._

        // this is what we receive from the customer
        sealed trait Request

        final case class InspectSummary(cartId: String, replyTo: ActorRef[Response]) extends Request

        //Note: a wrapped Shopping Cart response that's of type Request
        private final case class WrappedSCResponse(response: ShoppingCart.Response) extends Request

        // this is what we send to the customer
        sealed trait Response

        final case class Summary(cartId: String, amount: Double) extends Response
        // + some others


        /* Note:
           Checkout actor must handle Behaviour[Checkout.Request AND ShoppingCart.Response]
                -> anti-pattern
                -> each actor needs to support its onw requests and nothing else
                -> imagine having to handle responses  from multiple actors

                => We need to transform them to the type our actor handles (aka Checkout.Request)
         */

        def apply(shoppingCart: ActorRef[ShoppingCart.Request]): Behavior[Request] =
            Behaviors.setup { context =>
                // this actor will transform the ShoppingCart.Response to a Request
                val messageAdapter: ActorRef[ShoppingCart.Response] = context.messageAdapter(rsp => WrappedSCResponse(rsp))

                def handlingCheckouts(checkoutsInProgress: Map[String, ActorRef[Response]]): Behavior[Request] =
                    Behaviors.receiveMessage {
                        case InspectSummary(cartId, customer) =>
                            shoppingCart ! ShoppingCart.GetCurrentCart(cartId, messageAdapter)
                            handlingCheckouts(checkoutsInProgress + (cartId -> customer))

                        case WrappedSCResponse(rsp) =>
                            // logic for dealing with response from shopping cart
                            rsp match {
                                case CurrentCart(cartId, items) =>
                                    val summary = Summary(cartId, items.map(_.price).sum)
                                    val customer = checkoutsInProgress(cartId)
                                    customer ! summary
                                    handlingCheckouts(checkoutsInProgress - cartId)
                            }
                    }

                handlingCheckouts(Map())
            }
    }

    def main(args: Array[String]): Unit = {
        val rootBehavior: Behavior[Any] = Behaviors.setup { context =>
            val shoppingCart = context.spawn(ShoppingCart.dummy(), "shopping-cart")
            val checkout = context.spawn(Checkout(shoppingCart), name = "checkout")

            val customer = context.spawn(Behaviors.receiveMessage[Checkout.Response] {
                case Summary(_, amount) =>
                    println(s"Total to pay is $amount")
                    Behaviors.same
            }, "customer")

            checkout ! InspectSummary("123-abc-456", customer)

            //not important
            Behaviors.empty
        }

        val system = ActorSystem(rootBehavior, "main-app")
        implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())
        system.scheduler.scheduleOnce(1.second, () => system.terminate())

    }
}
