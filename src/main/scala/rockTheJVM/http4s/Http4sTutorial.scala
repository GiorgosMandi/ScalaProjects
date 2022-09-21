package rockTheJVM.http4s
import cats._
import cats.effect._
import cats.implicits._
import org.http4s.circe._
import org.http4s._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.dsl._
import org.http4s.dsl.impl._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server._
import java.util.Date


object Http4sTutorial {


    case class Person(name: String, lastName: String)
    case class Movies(id: Int, title: String, releaseDate: Date, actors: List[Person], director: Person)

    // HttpRoutes[F]: Request -> F[Option[Response]]
    /*
        - GET all movies for a director under a given year
        - GET all actors of a movie
        - POST add a new director
     */



}
