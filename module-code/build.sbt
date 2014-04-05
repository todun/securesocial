import play.Project._
import bintray.Plugin._
import bintray.Keys._

organization := "denisftw"

name := "securesocial"

version := "0.0.14-SNAPSHOT"

sbtPlugin := false

libraryDependencies ++= Seq(
  cache,
  "com.typesafe" %% "play-plugins-util" % "2.2.0",
  "com.typesafe" %% "play-plugins-mailer" % "2.2.0",
  "net.tanesha.recaptcha4j" % "recaptcha4j" % "0.0.7",
  "org.mindrot" % "jbcrypt" % "0.3m"
)

resolvers ++= Seq(Resolver.typesafeRepo("releases"))

bintraySettings

publishMavenStyle := true

licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

scalacOptions := Seq("-feature", "-deprecation")

playScalaSettings

repository in bintray := "maven"

bintrayOrganization in bintray := None
