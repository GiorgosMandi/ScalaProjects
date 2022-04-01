ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.12"

lazy val root = (project in file("."))
  .settings(
    name := "ScalaProjects"
  )
// https://mvnrepository.com/artifact/io.circe/circe-core
libraryDependencies += "io.circe" %% "circe-core" % "0.15.0-M1"
libraryDependencies += "io.circe" %% "circe-parser" % "0.15.0-M1"
libraryDependencies += "io.circe" %% "circe-generic" % "0.15.0-M1"

// Akka Essentials
val akkaVersion ="2.5.13"
// https://mvnrepository.com/artifact/com.typesafe.akka/akka-actor
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
// https://mvnrepository.com/artifact/com.typesafe.akka/akka-testkit
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion

val testVersion = "3.2.11"
libraryDependencies += "org.scalactic" %% "scalactic" % testVersion
libraryDependencies += "org.scalatest" %% "scalatest" % testVersion
