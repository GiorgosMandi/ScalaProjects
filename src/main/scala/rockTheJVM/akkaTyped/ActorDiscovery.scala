package rockTheJVM.akkaTyped
import akka.NotUsed
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._
import scala.util.Random

object ActorDiscovery {
    /*
    BLOG: https://blog.rockthejvm.com/akka-actor-discovery/
    IOT
    Sensors -> Actors

    Sensor Controller: upon starting, the sensor controller would send heartbeats every second

    Sensor: with every heartbeat, the sensors would send their sensor readings containing their id and the reading
            to the data aggregator

    Guardian: after 10 seconds of work, the guardian transparently swaps the data aggregator with a new one

    Data Aggregator: display the latest data (here we’ll simply log the data)
        - the sensors can “magically” find the data aggregator to send data to, published by the guardian actor
        - the sensors will continue to work and push the data to the new aggregator

    END: after 20 seconds, the entire application will be terminated
     */

    //data aggregator domain
    case class SensorReading(id: String, value: Double)
    object DataAggregator{
        val serviceKey: ServiceKey[SensorReading] = ServiceKey[SensorReading]("dataAggregator")

        def active(latestReadings: Map[String, Double]): Behavior[SensorReading] =
            Behaviors.receive { (context, reading) =>
                val id = reading.id
                val value = reading.value
                val newReadings = latestReadings + (id -> value)

                // "display" part - in real life this would feed a graph, a data ingestion engine or processor
                context.log.info(s"[${context.self.path.name}] Latest readings: $newReadings")
                active(newReadings)
            }

        def apply(): Behavior[SensorReading] = active(Map())

    }

    trait SensorCommand
    case object SensorHeartbeat extends SensorCommand
    case class ChangeDataAggregator(agg: Option[ActorRef[SensorReading]]) extends SensorCommand
    object Sensor {

        def activeSensor(id: String, aggregator: Option[ActorRef[SensorReading]]): Behavior[SensorCommand] =
            Behaviors.receiveMessage {
                case SensorHeartbeat =>
                    // send the data to the aggregator
                    aggregator.foreach(_ ! SensorReading(id, Random.nextDouble() * 40))
                    Behaviors.same
                case ChangeDataAggregator(newAgg) =>
                    // swap the aggregator for the new one
                    activeSensor(id, newAgg)

            }

        // the sensor actor
        def apply(id: String): Behavior[SensorCommand] = Behaviors.setup { context =>
            // use a message adapter to turn a receptionist listing into a SensorCommand
            val receptionistSubscriber: ActorRef[Receptionist.Listing] = context.messageAdapter {
                case DataAggregator.serviceKey.Listing(set) => ChangeDataAggregator(set.headOption)
            }

            // subscribe to the receptionist service key
            context.system.receptionist ! Receptionist.Subscribe(DataAggregator.serviceKey, receptionistSubscriber)
            activeSensor(id, None)
        }

        // the sensor aggregator
        def controller(): Behavior[NotUsed] = Behaviors.setup { context =>
            val sensors = (1 to 10).map(i => context.spawn(Sensor(s"sensor_$i"), s"sensor_$i"))
            val logger = context.log // used so that we don't directly use context inside the lambda below
            // send heartbeats every second
            import context.executionContext
            context.system.scheduler.scheduleAtFixedRate(1.second, 1.second) { () =>
                logger.info("Heartbeat")
                sensors.foreach(_ ! SensorHeartbeat)
            }
            Behaviors.empty
        }
    }

    val guardian: Behavior[NotUsed] = Behaviors.setup {context =>
        // controller for the sensors
        context.spawn(Sensor.controller(), "controller")

        // publish dataAgg1 is available by associating to the service key of DataAggregator
        val dataAgg1 = context.spawn(DataAggregator(), "data_agg1")
        context.system.receptionist ! Receptionist.register(DataAggregator.serviceKey, dataAgg1)

        Thread.sleep(10000)

        // de-registering dataAgg1 - We can have multiple actors registered to the same service key
        context.log.info("[guardian]: Changing data aggregator")
        context.system.receptionist ! Receptionist.deregister(DataAggregator.serviceKey, dataAgg1)

        // publish dataAgg2 is available by associating to the service key of DataAggregator
        val dataAgg2 = context.spawn(DataAggregator(), "data_agg_2")
        context.system.receptionist ! Receptionist.register(DataAggregator.serviceKey, dataAgg2)

        Behaviors.empty
    }

    def main(args: Array[String]): Unit ={
        val conf: Config = ConfigFactory.load()

        val system = ActorSystem(guardian, "ActorDiscovery", conf)
        import system.executionContext
        system.scheduler.scheduleOnce(20.seconds, () => system.terminate())
    }
}
