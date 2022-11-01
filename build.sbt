ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.12"


lazy val root = (project in file("."))
  .settings(
    name := "ScalaProjects"
  )

// Circe
val CirceVersion = "0.14.0-M5"
libraryDependencies += "io.circe" %% "circe-core" % CirceVersion
libraryDependencies += "io.circe" %% "circe-parser" % CirceVersion
libraryDependencies += "io.circe" %% "circe-generic" % CirceVersion

// HTTP4s
val Http4sVersion = "1.0.0-M21"
libraryDependencies ++= Seq(
    "org.http4s"      %% "http4s-blaze-server" % Http4sVersion,
    "org.http4s"      %% "http4s-circe"        % Http4sVersion,
    "org.http4s"      %% "http4s-dsl"          % Http4sVersion,
    "io.circe"        %% "circe-generic"       % CirceVersion,
)

// Akka Essentials
val akkaVersion ="2.6.5"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % akkaVersion


// Akka Streams
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion


val akkaHttpVersion = "10.2.0"
libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
)


// zio
val zioVersion = "2.0.2"
libraryDependencies += "dev.zio" %% "zio" % zioVersion

val testVersion = "3.2.11"
libraryDependencies += "org.scalactic" %% "scalactic" % testVersion
libraryDependencies += "org.scalatest" %% "scalatest" % testVersion
