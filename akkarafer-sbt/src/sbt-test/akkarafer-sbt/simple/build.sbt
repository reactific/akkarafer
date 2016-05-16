import com.reactific.sbt.settings._

name := "hello-test"

scalaVersion := "2.11.6"

organization := "com.reactific"

maxErrors := 10

unmanagedResourceDirectories in compile := Seq(baseDirectory.value / "src/resources")

enablePlugins(AkkaraferSbtPlugin,PublishUniversalPlugin)

copyrightHolder := "Reactific Software LLC"

copyrightYears := Seq(2015)

developerUrl := url("http://reactific.com/")

titleForDocs := "Yo!"

codePackage := "com.reactific.akkarafer.sbt"

