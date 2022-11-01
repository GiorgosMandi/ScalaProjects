package rockTheJVM.akkaHttp

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import spray.json._


// Circe is related to Typelevel, Spray is related to Akka
import java.util.UUID

case class Person(name: String, age: Int)
case class UserAdded(id: String, timestamp: Long)

trait PersonJsonProtocol extends DefaultJsonProtocol{
    implicit val personFormat: RootJsonFormat[Person] = jsonFormat2(Person) // jsonFormat2 because Person has 2 arguments
    implicit val userAddedFormat: RootJsonFormat[UserAdded] = jsonFormat2(UserAdded)
}

object AkkaHttpJson extends PersonJsonProtocol with SprayJsonSupport{


    implicit val system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "AkkaHttpJson")

    val route: Route = (path("api" / "user") & post) {
        // `as` fetches whatever implicit converter you have for that type
        entity(as[Person]) {  person: Person =>
            complete(UserAdded(UUID.randomUUID().toString, System.currentTimeMillis()))
        }
    }


    def main(args: Array[String]): Unit = {
        Http().newServerAt("localhost", 8080).bind(route)

    }

}
