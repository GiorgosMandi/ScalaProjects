package rockTheJVM.akkaEssentials.part6patterns

import akka.actor.{ActorRef, ActorSystem, FSM, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.language.postfixOps

class VendingMachineFSMTest extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with AnyWordSpecLike with BeforeAndAfterAll{
    import VendingMachineFSM.VendingError._
    import VendingMachineFSM._

    override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

    "Vending FSM Machine" should {
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



object VendingMachineFSM{

    trait VendingState
    case object Idle extends VendingState
    case object Operational extends VendingState
    case object WaitForMoney extends VendingState

    trait VendingData
    case object Unitialize extends VendingData
    case class Initialize(inventory: Map[String, Int], prices: Map[String, Int]) extends VendingData
    case class WaitForMoneyData(inventory: Map[String, Int], prices: Map[String, Int], product: String, money: Int, requester: ActorRef) extends VendingData

    case class Instructions(instructions: String) // the message the vm will show on screen
    case class RequestProduct(product: String)
    case class ReceiveMoney(money: Int)
    case class Deliver(product: String)
    case class GiveBackChange(amount: Int)
    case class VendingError(reason: String)
    case object ReceiveMoneyTimeout


    object VendingError{
        val NOT_INITIALIZED = "Machine not initialized"
        val PRODUCT_NOT_AVAILABLE = "Product not available"
        val TIMED_OUT = "Timed Out"
        val COMMAND_NOT_FOUND = "Command not found"
    }

    class VendingMachineActor extends FSM[VendingState, VendingData] {
        import VendingError._

        // we don't have a receiver handler

        // messages triggers an EVENT(message, data)
        // in FSM we handle states and events, not messages


        /*
            state, data

            event -> state and data may change

            state = Idle
            data = Unitialized

            event(Initialize(inventory, prices))
                ->
                    state = Operational
                    data = Initialized(inventory, prices)

            ---
            state = Operational
            data = Initialized(inventory, prices)

            event(RequestedProduct(coke))
                ->
                    state = WaitForMoney
                    data =  WaitForMoneyData(Map(coke -> 10), Map(coke -> 1), coke, 0, R)

            ---
            state = WaitForMoney
            data = WaitForMoney(amount)

            event(ReceiveMoney(2))
                ->
                    state = Operational
                    data = Initialized(inventory, prices)
         */

        startWith(Idle, Unitialize)

        when(Idle) {
            case Event(Initialize(inventory, prices), Unitialize) =>
                goto(Operational) using Initialize(inventory, prices)
            case _ =>
                sender() ! VendingError(NOT_INITIALIZED)
                stay()
        }

        when(Operational) {
            case Event(RequestProduct(product), Initialize(inventory, prices)) =>
                inventory.get(product) match {
                    case None | Some(0) =>
                        sender() ! VendingError(PRODUCT_NOT_AVAILABLE)
                        stay()
                    case Some(_) =>
                        val money = prices(product)
                        sender() ! Instructions(s"Please insert $money dollars")
                        goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, 0 ,sender())
                }
        }

        when(WaitForMoney, stateTimeout = 1 second) {
            case Event(StateTimeout, WaitForMoneyData(inventory, prices, _, money, requester)) =>
                requester ! VendingError(TIMED_OUT)
                if (money > 0)
                    requester ! GiveBackChange(money)
                goto(Operational) using Initialize(inventory, prices)

            case Event(ReceiveMoney(amount), WaitForMoneyData(inventory, prices, product, money, requester)) =>
                val totalMoney = money + amount
                val price = prices(product)
                if (totalMoney >= price){
                    // user buys the product
                    requester ! Deliver(product)
                    // deliver the changes
                    if (totalMoney - price > 0) requester ! GiveBackChange(totalMoney - price)
                    // updating inventory
                    val newStock = inventory(product)-1
                    val newInventory = inventory + (product -> newStock)
                    goto(Operational) using Initialize(newInventory, prices)
                }
                else {
                    val rest = price - totalMoney
                    requester ! Instructions(s"Please insert $rest dollars")
                    goto(WaitForMoney) using WaitForMoneyData(inventory, prices, product, totalMoney, requester)
                }
        }

        whenUnhandled {
            case Event(_, _) =>
                sender() ! VendingError(COMMAND_NOT_FOUND)
                stay()
        }

        onTransition{
            case stateA -> stateB => log.info(s"Transition from $stateA to $stateB")
        }

        initialize()
    }
}

