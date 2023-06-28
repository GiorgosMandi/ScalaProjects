package scalaExercises.cats

import scalaExercises.cats.Validated.Validated.{Invalid, Valid}

object Validated {

    sealed abstract class ConfigError
    final case class MissingConfig(field: String) extends ConfigError
    final case class ParseError(field: String) extends ConfigError

    sealed abstract class Validated[+E, +A]
    object Validated {
        final case class Valid[+A](a: A) extends Validated[Nothing, A]
        final case class Invalid[+E](e: E) extends Validated[E, Nothing]
    }

    trait Read[A] {
        def read(s: String): Option[A]
    }

    object Read {
        def apply[A](implicit A: Read[A]): Read[A] = A

        implicit val stringRead: Read[String] = (s: String) => Some(s)

        implicit val intRead: Read[Int] = (s: String) =>
            if (s.matches("-?[0-9]+")) Some(s.toInt)
            else None
    }

    case class Config(map: Map[String, String]) {
        def parse[A: Read](key: String): Validated[ConfigError, A] =
            map.get(key) match {
                case None => Invalid(MissingConfig(key))
                case Some(value) => Read[A].read(value) match {
                        case None => Invalid(ParseError(key))
                        case Some(a) => Valid(a)
                    }
            }
    }
}
