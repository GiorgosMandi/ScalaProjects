package rockTheJVM.akkaEssentials.part1scalaBasics

import scala.concurrent.Future
import scala.language.implicitConversions

object AdvancedScala extends App{

    // I) PARTIAL FUNCTIONS

    // operates only for these values, otherwise throws a exception (Math error)
    // this can be rewritten using pattern matching
    val partialFunction: PartialFunction[Int, Int] = {
        case 1 => 42
        case 2 => 65
        case 5 => 99
    }

    // this can be re-written as
    val modifiedList = List(1,2,3).map {
        case 1 => 42
        case _ => 0
    }

    // convert partial function to a normal function using option
    val lifted: Int => Option[Int] = partialFunction.lift
    println(s"1 lifted -> ${lifted(1)}")
    println(s"42 lifted -> ${lifted(42)}")

    // extend partial functions with new patterns
    val pfChain = partialFunction.orElse[Int, Int]{
        case 60 => 9000
    }

    type ReceiveFunction = PartialFunction[Any, Unit]
    def receiveFunction: ReceiveFunction = {
        case x: Int => println(s"Input Int $x")
        case s: Int => println(s"Input String $s")
        case _ => println("Unknown")
    }

    receiveFunction(2)
    receiveFunction()



    // II)  IMPLICITS
    // implicit function
    case class Person(name: String){
        def greet(): Unit = println(s"Hello, my name is ${name}")
    }
    implicit def fromNameToPerson(name: String): Person = Person(name)
    // Compiler runs fromNameToPerson("Peter").greet()
    "Peter".greet()

    // implicit class
    implicit class Dog(name:String = "Lacy") {
        def bark(): Unit = println("Wuf")
    }
    // Compiler runs Dog("Lucy).bark
    "Lacy".bark()

    // import implicit from local scope
    implicit val reverseOrdering:Ordering[Int] = Ordering.fromLessThan(_ > _)
    println(List(1,3,4,2,7,5).sorted)

    // import implicits from library
    import scala.concurrent.ExecutionContext.Implicits._
    val future = Future{
        println("Hello From the future")
    }

    // Import implicit from companion object
    object Person {
        implicit val person: Ordering[Person] = Ordering.fromLessThan( (a,b) => a.name.compareTo(b.name) > 0)
    }
    val people = List(Person("George"), Person("Irene"), Person("Teo"))
    println(people.sorted)
}
