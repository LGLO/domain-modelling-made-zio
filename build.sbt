name := "domain-modelling-made-functional"
description := "Domain Modelling Made Functional Book project with Scala"
version := "0.0.1"
// scalaVersion := "0.23.0-RC1"
scalaVersion in ThisBuild := "2.13.1"

addCommandAlias("fix", "; all compile:scalafix test:scalafix")

scalafmtOnCompile := true

val ZioVersion = "1.0.0-RC18-2"
val ZioLoggingVersion = "0.2.7"
val Http4sVersion = "0.21.3"
val CirceVersion = "0.13.0"
val Specs2Version = "4.9.3"
val LogbackVersion = "1.2.3"

lazy val root = (project in file("."))
  .settings(
    organization := "non",
    name := "http4s-test",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.1",
    fork := true,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    exportJars := true,
    updateOptions := updateOptions.value.withCachedResolution(true),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "com.softwaremill.sttp.client" %% "circe" % "2.1.0-RC1",
      "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.1.0-RC1",
      "com.sksamuel.avro4s" % "avro4s-core_2.13" % "3.0.9",
      "io.circe" %% "circe-generic" % CirceVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "org.specs2" %% "specs2-core" % Specs2Version % "test",
      "dev.zio" %% "zio" % ZioVersion,
      "dev.zio" %% "zio-config" % "1.0.0-RC16-2",
      "dev.zio" %% "zio-config-magnolia" % "1.0.0-RC16-2",
      "dev.zio" %% "zio-config-typesafe" % "1.0.0-RC16-2",
      "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC12",
      "dev.zio" %% "zio-kafka"   % "0.8.0",
      "dev.zio" %% "zio-test" % ZioVersion % "test",
      "dev.zio" %% "zio-logging" % ZioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j" % ZioLoggingVersion,
      "dev.zio" %% "zio-test-sbt" % ZioVersion % "test"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
  )


scalafixDependencies in ThisBuild+= "com.nequissimus" %% "sort-imports" % "0.3.0"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  "-Wunused",
  "-Xfatal-warnings"
)
