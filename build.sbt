name := "domain-modelling-made-functional"
description := "Domain Modelling Made Functional Book project with Scala"
version := "0.0.1"
// scalaVersion := "0.23.0-RC1"
scalaVersion in ThisBuild := "2.13.1"

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
      "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
      "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
      "org.http4s" %% "http4s-circe" % Http4sVersion,
      "org.http4s" %% "http4s-dsl" % Http4sVersion,
      "dev.zio" %% "zio" % ZioVersion,
      "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC12",
      "io.circe" %% "circe-generic" % CirceVersion,
      "org.specs2" %% "specs2-core" % Specs2Version % "test",
      "dev.zio" %% "zio-test" % ZioVersion % "test",
      "dev.zio" %% "zio-test-sbt" % ZioVersion % "test",
      "dev.zio" %% "zio-logging" % ZioLoggingVersion,
      "dev.zio" %% "zio-logging-slf4j" % ZioLoggingVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3")
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-language:higherKinds",
  "-language:postfixOps",
  "-feature",
  //"-Wunused",
  "-Xfatal-warnings"
)
