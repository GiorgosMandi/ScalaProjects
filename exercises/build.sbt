ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.12.18"

lazy val root = (project in file("."))
  .settings(
    name := "exercises"
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

// zio
val zioVersion = "2.0.2"
libraryDependencies += "dev.zio" %% "zio" % zioVersion

val testVersion = "3.2.11"
libraryDependencies += "org.scalactic" %% "scalactic" % testVersion
libraryDependencies += "org.scalatest" %% "scalatest" % testVersion