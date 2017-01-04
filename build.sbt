lazy val root = (project in file("."))
  .settings(
    name := "distinct-set",
    version := "1.0",

    scalaVersion := "2.12.1",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.4.16",

      "org.scalatest" %% "scalatest" % "3.0.1" % "test"
    )
  )
