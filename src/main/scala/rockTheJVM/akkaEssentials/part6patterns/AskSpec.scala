package rockTheJVM.akkaEssentials.part6patterns

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

// import the ask pattern
import akka.pattern.ask
import akka.pattern.pipe
class AskSpec extends TestKit(ActorSystem("AskSpec")) with ImplicitSender with AnyWordSpecLike with BeforeAndAfterAll {

    import AskSpec._
    import AskSpec.AuthManager._
    override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

    "an Authenticator" should {
        "fail to authenticate an non-registered user" in {
            val authManager = system.actorOf(Props[AuthManager])
            authManager ! Authenticate("geo", "geo")
            expectMsg(AuthFailure(USERNAME_NOT_FOUND))
        }
        "fail to authenticate if invalid password" in {
            val authManager = system.actorOf(Props[AuthManager])
            authManager ! RegisterUser("geo", "geo")
            authManager ! Authenticate("geo", "g3o")
            expectMsg(AuthFailure(WRONG_PASSWORD))
        }

        "succeed to authenticate if invalid password" in {
            val authManager = system.actorOf(Props[AuthManager])
            authManager ! RegisterUser("geo", "geo")
            authManager ! Authenticate("geo", "geo")
            expectMsg(AuthSuccess)
        }
    }
    "a piped Authenticator" should {
        "fail to authenticate an non-registered user" in {
            val authManager = system.actorOf(Props[PipedAuthManager])
            authManager ! Authenticate("geo", "geo")
            expectMsg(AuthFailure(USERNAME_NOT_FOUND))
        }
        "fail to authenticate if invalid password" in {
            val authManager = system.actorOf(Props[PipedAuthManager])
            authManager ! RegisterUser("geo", "geo")
            authManager ! Authenticate("geo", "g3o")
            expectMsg(AuthFailure(WRONG_PASSWORD))
        }

        "succeed to authenticate if invalid password" in {
            val authManager = system.actorOf(Props[PipedAuthManager])
            authManager ! RegisterUser("geo", "geo")
            authManager ! Authenticate("geo", "geo")
            expectMsg(AuthSuccess)
        }
    }
}
object AskSpec {

    case class Read(key: String)
    case class Write(key: String, value: String)
    class KVActor extends Actor with ActorLogging {
        override def receive: Receive = online(Map())

        def online(kv: Map[String, String]): Receive ={
            case Read(k) =>
                log.info(s"Trying to read the value at the key $k")
                sender() ! kv.get(k)
            case Write(k, v) =>
                log.info(s"Writing value $v for the key $k")
                context.become(online(kv + (k -> v)))
        }
    }

    // User Authenticator actor
    case class RegisterUser(username: String, password: String)
    case class Authenticate(username: String, password: String)
    case class AuthFailure(message: String)
    case object AuthSuccess
    object AuthManager {
        val USERNAME_NOT_FOUND = "Username not found"
        val WRONG_PASSWORD = "Wrong password"
        val SYSTEM_ERROR = "System error"
    }

    class AuthManager extends Actor with ActorLogging {

        import AuthManager._

        // step 2 - logistics
        implicit val timeout: Timeout = Timeout(1 second)
        implicit val executionContext: ExecutionContext = context.dispatcher

        protected val authDB: ActorRef = context.actorOf(Props[KVActor], "kvDB")

        override def receive: Receive = {
            case RegisterUser(username, password) => authDB ! Write(username, password)
            case Authenticate(username, password) => handleAuthentication(username, password)
        }

        def handleAuthentication(username: String, password: String): Unit = {
            val originalSender = sender()
            // step 3 - ask the actor
            val future: Future[Any] = authDB ? Read(username)
            // step 4 - handle the future for e.g. with onComplete
            future.onComplete {
                // step 5 - NEVER CALL METHODS ON THE ACTOR INSTANCE OR ACCESS MUTABLE STATES IN ON-COMPLETE
                // avoid closing over the actor instance or mutable state
                case Success(None) => originalSender ! AuthFailure(USERNAME_NOT_FOUND)
                case Success(Some(dbPassword)) =>
                    if (dbPassword == password) originalSender ! AuthSuccess
                    else originalSender ! AuthFailure(WRONG_PASSWORD)
                case Failure(_) => originalSender ! AuthFailure(SYSTEM_ERROR)
            }
        }
    }

    class PipedAuthManager extends AuthManager {
        import AuthManager._

        override def handleAuthentication(username: String, password: String): Unit = {
            // step 3 - ask the actor
            val future = authDB ?  Read(username)
            // step 4 - process the future until you get the response you will send back
            val passwordFuture = future.mapTo[Option[String]]
            val responseFuture = passwordFuture.map {
                case None => AuthFailure(USERNAME_NOT_FOUND)
                case Some(dbPassword) =>
                    if (dbPassword == password) AuthSuccess
                    else AuthFailure(WRONG_PASSWORD)
            } // This will be completed with the response I will send back

            /*
             step 5 - pipe the resulting future to the actor you want to send the result to
             When the future completes send the response to the actor ref in the arg list
             */
            responseFuture.pipeTo(sender())
        }
    }

}
