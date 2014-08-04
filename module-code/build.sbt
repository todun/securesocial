import bintray.Plugin._

import bintray.Keys._

organization := "denisftw"

name := "securesocial"

version := "0.0.19"

sbtPlugin := false

scalaVersion := "2.10.4"

libraryDependencies ++= Seq(
  cache,
  ws,
  "com.typesafe.play.plugins" %% "play-plugins-util" % "2.3.0",
  "com.typesafe.play.plugins" %% "play-plugins-mailer" % "2.3.0",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "org.specs2" %% "specs2" % "2.3.12" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test",
  "net.tanesha.recaptcha4j" % "recaptcha4j" % "0.0.7"
)

resolvers ++= Seq(
  Resolver.typesafeRepo("releases")
)

bintraySettings

publishMavenStyle := true

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalacOptions := Seq("-feature", "-deprecation")

repository in bintray := "maven"

bintrayOrganization in bintray := None