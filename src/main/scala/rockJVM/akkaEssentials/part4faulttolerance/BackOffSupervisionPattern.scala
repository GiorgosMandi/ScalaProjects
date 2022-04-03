package rockJVM.akkaEssentials.part4faulttolerance

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffSupervisor}

import java.io.File
import scala.io.Source
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * @author George Mandilaras (NKUA)
 */
object BackOffSupervisionPattern extends App{

    case object ReadFile
    class FileBasedPersistentActor extends Actor with ActorLogging{
        var dataSource: Source = null

        override def preStart(): Unit =
            log.info("PersistentActor is starting")
        override def postStop(): Unit =
            log.info("PersistentActor stopped")

        override def preRestart(reason: Throwable, message: Option[Any]): Unit =
            log.info("PersistentActor is restarting")

        override def receive: Receive = {
            case ReadFile =>
                // file does not exists
                if (dataSource == null)
                    dataSource = Source.fromFile(new File("src/main/resources/testFile/important_.txt"))
                log.info("I just read some important data: " + dataSource.getLines().toList)
        }
    }

    val system = ActorSystem("BackOffSupervisionPattern")
    val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
    simpleActor ! ReadFile
    // fails because the file does not exists

    Thread.sleep(1000)
    print("\n\n\n")

    /**
     * simpleBackOffSupervisor
     *  - child called simpleBackOffActor (props of type FileBasedPersistentActor)
     *  - supervision strategy is the default one (restarting on everything)
     *      - first attempt after 3 seconds
     *      - next attempt after 2x the previous attempt
     */
    val simpleBackOffSupervisorProps = BackoffSupervisor.props(
        Backoff.onFailure(Props[FileBasedPersistentActor],
            "simpleBackOffActor",
            3 seconds, // then 6s, 12s, 24s and reached the limit of 30s
            30 seconds, // limit, upper time limit
            0.2d // introduces a little noise so not all threads run simultaneously
        )
    )
    val simpleBackOffSupervisor = system.actorOf(simpleBackOffSupervisorProps, "simpleSupervisor")
    simpleBackOffSupervisor ! ReadFile

    Thread.sleep(1000)
    print("\n\n\n")

    class EagerFBPActor extends FileBasedPersistentActor {

        // tries to read file on start
        override def preStart(): Unit = {
            log.info("EagerFPA is starting")
            dataSource = Source.fromFile(new File("src/main/resources/testFile/important.txt"))
        }
    }

    /**
     * it retries, and when it recovers and succeeds it retrieves the data
     */
    val stopBackOffSupervisorProps = BackoffSupervisor.props(
        Backoff.onStop(Props[EagerFBPActor],
            "stopBackOffActor",
            1 seconds, // then 6s, 12s, 24s and reached the limit of 30s
            30 seconds, // limit, upper time limit
            0.1d // introduces a little noise so not all threads run simultaneously
        ).withSupervisorStrategy (OneForOneStrategy(){
            case _ => Stop
        })
    )

    val stopBackOffSupervisor = system.actorOf(stopBackOffSupervisorProps, "stopSupervisor")
    stopBackOffSupervisor ! ReadFile

}
