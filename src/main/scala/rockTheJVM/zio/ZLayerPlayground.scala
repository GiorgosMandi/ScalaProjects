//package rockTheJVM.zio
//
//import rockTheJVM.zio.ZLayerPlayground.UserDb.UserDbEnv
//import rockTheJVM.zio.ZLayerPlayground.UserEmailer.UserEmailerEnv
//import zio.{Console, IO, Task, UIO, ZIO, ZLayer}
//
//import java.io.IOException
//
//object ZLayerPlayground extends zio.ZIOAppDefault {
//
//    // ZIO[-R, +E, +A] = "effects"
//    // - -R: an input or an environment
//    // - +E: error the service may fail
//    // - +A: Success Type
//
//    // R => Either[E, A]
//
//    val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
//
//    val aFailure: IO[String, Nothing] = ZIO.fail("Something went wrong")
//
//    val greetings: ZIO[Any, IOException, Unit] = for {
//        _ <- Console.printLine("Hi whats your name")
//        name <- Console.readLine
//        _ <- Console.printLine(s"Hi $name")
//    } yield ()
//
//
//    /*
//    Creating heavy apps involve services
//     - interacting with storage layer
//     - business logic
//     - front-facing APIs e.g. through Http
//     - communicating with other services
//     */
//
//    case class User(name: String, email: String)
//
//    trait UserEmailer {
//        def notify(user: User, message: String): UIO[Unit] // ZIO[Any, Throwable, Unit]
//    }
//    object UserEmailer {
//        override def notify(user: User, message: String): URIO[UserEmailer, Unit] =
//            ZIO.serviceWithZIO(_.notify(user, message))
//    }
////    println(s"[UserEmailer]: Sending $message to ${user.email}")
//    // Implementation of the Service Interface
//    case class UserEmailerLive(console: Console) extends UserEmailer {
//        override def notify(input: String): UIO[Unit] =
//            for {
//                console <- ZIO.service[Console]
//            } yield ()
//    }
//
//        // service implementation
//        val live: ZLayer[Console, Nothing, UserEmailerEnv] = ZLayer {
//            for (
//                console <- ZIO.service[Console]
//            ) yield EmailerEnvLive(console)
//        }
//
//        // front- facing API
//        def notify(user: User, message: String): ZIO[UserEmailerEnv, Throwable, Unit] =
//            ZIO.environmentWithZIO(service => service.get.notify(user, message))
//    }
//
//
//    object UserDb {
//        type UserDbEnv = UserDb.Service
//
//        trait Service {
//            def insert(user: User): Task[Unit]
//        }
//
//        case class UserDbLive(console: Console) extends UserDbEnv {
//            override def insert(user: User): Task[Unit] = console.printLine(s"[Database]: insert into public.user values('${user.email}')")
//        }
//
//        val live: ZLayer[Console, Nothing, UserDbEnv] = ZLayer {
//            for (
//                console <- ZIO.service[Console]
//            ) yield UserDbLive(console)
//        }
//
//        def insert(user: User): ZIO[UserDbEnv, Throwable, Unit] =
//            ZIO.environmentWithZIO(service => service.get.insert(user))
//    }
//
//    // composing ZLayers
//    // HORIZONTAL COMPOSITION
//    // ZLayer[In1, E1, Out1] ++ ZLayer[In2, E2, Out2] => ZLayer[In1 with In2, super(E1, E2), Out1 with Out2]
//
//    val userBackendLayer: ZLayer[Console, Nothing, UserDbEnv with UserEmailerEnv] = UserDb.live ++ UserEmailer.live
//    val user: User = User("George", "gmand@tade.com")
//    val message: String = "Learning ZIO v2"
//
//    def run: ZIO[Any, Nothing, Any] = {
//        UserEmailer
//          .notify(user, message)
//          .provideLayer(userBackendLayer)
//          .exitCode
//    }
//}
