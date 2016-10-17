name := """distinct-set"""

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.4.11",

  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)
