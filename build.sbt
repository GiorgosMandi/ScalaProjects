ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.12"
val Http4sVersion = "1.0.0-M21"
val CirceVersion = "0.14.0-M5"

lazy val root = (project in file("."))
  .settings(
    name := "ScalaProjects"
  )
// https://mvnrepository.com/artifact/io.circe/circe-core
libraryDependencies += "io.circe" %% "circe-core" % CirceVersion
libraryDependencies += "io.circe" %% "circe-parser" % CirceVersion
libraryDependencies += "io.circe" %% "circe-generic" % CirceVersion

// HTTP4s
libraryDependencies ++= Seq(
    "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
    "org.http4s"      %% "http4s-circe"        % Http4sVersion,
    "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
    "io.circe"        %% "circe-generic"       % CirceVersion,
)
// Akka Essentials
val akkaVersion ="2.5.19"
// https://mvnrepository.com/artifact/com.typesafe.akka/akka-actor
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
// https://mvnrepository.com/artifact/com.typesafe.akka/akka-testkit
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion


// Akka Streams
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion

val testVersion = "3.2.11"
libraryDependencies += "org.scalactic" %% "scalactic" % testVersion
libraryDependencies += "org.scalatest" %% "scalatest" % testVersion
