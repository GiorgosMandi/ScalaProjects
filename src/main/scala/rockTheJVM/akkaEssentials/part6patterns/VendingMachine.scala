package rockTheJVM.akkaEssentials.part6patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Cancellable, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

class VendingMachine extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with AnyWordSpecLike with BeforeAndAfterAll{
    import VendingMachine.VendingError._
    import VendingMachine._

    override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

    "Vending Machine" should {
        "Fail when not initialized" in {
            val vendingMachine = system.actorOf(Props[VendingMachineActor])
            vendingMachine ! RequestProduct("coke")
            expectMsg(VendingError(NOT_INITIALIZED))
        }

        "Fail when product is not available" in {
            val vendingMachine = system.actorOf(Props[VendingMachineActor])
            vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
            vendingMachine ! RequestProduct("sandwich")
            expectMsg(VendingError(PRODUCT_NOT_AVAILABLE))
        }

        "Throw timeout if we don't insert money" in {
            val vendingMachine = system.actorOf(Props[VendingMachineActor])
            vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 1))
            vendingMachine ! RequestProduct("coke")
            expectMsg(Instructions(s"Please insert 1 dollars"))

            within(1.5 second){
                expectMsg(VendingError(TIMED_OUT))
            }
        }
        "Handle the reception of partial money " in {
            val vendingMachine = system.actorOf(Props[VendingMachineActor])
            vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
            vendingMachine ! RequestProduct("coke")
            expectMsg(Instructions(s"Please insert 3 dollars"))

            vendingMachine ! ReceiveMoney(1)
            expectMsg(Instructions(s"Please insert 2 dollars"))

            vendingMachine ! ReceiveMoney(1)
            expectMsg(Instructions(s"Please insert 1 dollars"))

            vendingMachine ! ReceiveMoney(1)
            expectMsg(Deliver("coke"))
        }

        "Handle the case of giving back changes " in {
            val vendingMachine = system.actorOf(Props[VendingMachineActor])
            vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
            vendingMachine ! RequestProduct("coke")
            expectMsg(Instructions(s"Please insert 3 dollars"))
            vendingMachine ! ReceiveMoney(4)
            expectMsg(Deliver("coke"))
            expectMsg(GiveBackChange(1))
        }

        "Buy product and be available for a new order" in {
            val vendingMachine = system.actorOf(Props[VendingMachineActor])
            vendingMachine ! Initialize(Map("coke" -> 10), Map("coke" -> 3))
            vendingMachine ! RequestProduct("coke")
            expectMsg(Instructions(s"Please insert 3 dollars"))
            vendingMachine ! ReceiveMoney(4)
            expectMsg(Deliver("coke"))
            expectMsg(GiveBackChange(1))

            vendingMachine ! RequestProduct("coke")
            expectMsg(Instructions(s"Please insert 3 dollars"))
        }
    }

}

object VendingMachine{

    object VendingError{
        val NOT_INITIALIZED = "Machine not initialized"
        val PRODUCT_NOT_AVAILABLE = "Product not available"
        val TIMED_OUT = "Timed Out"
    }

    case class Initialize(inventory: Map[String, Int], prices: Map[String, Int])
    case class RequestProduct(product: String)
    case class Instructions(instructions: String) // the message the vm will show on screen
    case class ReceiveMoney(money: Int)
    case class Deliver(product: String)
    case class GiveBackChange(amount: Int)
    case class VendingError(reason: String)
    case object ReceiveMoneyTimeout

    class VendingMachineActor extends Actor with ActorLogging {
        import VendingError._

        override def receive: Receive = idle
        implicit val executionContext: ExecutionContextExecutor = context.dispatcher

        def idle: Receive = {
            case Initialize(inventory, prices) => context.become(operational(inventory, prices))
            case _ => sender() ! VendingError(NOT_INITIALIZED)
        }

        def operational(inventory: Map[String, Int], prices: Map[String, Int]): Receive = {
            case RequestProduct(product) => inventory.get(product) match {
                case None | Some(0) =>
                    sender() ! VendingError(PRODUCT_NOT_AVAILABLE)
                case Some(_) =>
                    val price = prices(product)
                    sender() ! Instructions(s"Please insert $price dollars")

                    context.become(waitForMoney(inventory, prices, product, 0, startReceiveMoneyTimeoutSchedule, sender()))
            }
        }

        def waitForMoney(
                        inventory: Map[String, Int],
                        prices: Map[String, Int],
                        product: String,
                        money: Int,
                        moneyTimeoutSchedule: Cancellable,
                        requester: ActorRef
                        ): Receive = {
            case ReceiveMoneyTimeout =>
                requester ! VendingError(TIMED_OUT)
                if (money > 0)
                    requester ! GiveBackChange(money)
                context.become(operational(inventory, prices))
            case ReceiveMoney(amount) =>
                val totalMoney = money + amount
                moneyTimeoutSchedule.cancel()
                val price = prices(product)
                if (totalMoney >= price){
                    // user buys the product
                    requester ! Deliver(product)
                    // deliver the changes
                    if (totalMoney - price > 0) requester ! GiveBackChange(totalMoney - price)
                    // updating inventory
                    val newStock = inventory(product)-1
                    val newInventory = inventory + (product -> newStock)
                    context.become(operational(newInventory, prices))
                }
                else {
                    val rest = price - totalMoney
                    requester ! Instructions(s"Please insert $rest dollars")
                    context.become(waitForMoney(
                                        inventory,
                                        prices,
                                        product,
                                        totalMoney,
                                        startReceiveMoneyTimeoutSchedule,
                                        requester)
                    )
                }
        }

        def startReceiveMoneyTimeoutSchedule: Cancellable = context.system.scheduler.scheduleOnce(1 second){
            self ! ReceiveMoneyTimeout
        }
    }
}
