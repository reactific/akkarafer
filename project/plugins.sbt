
libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value

libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.12"

scalaVersion := "2.10.5"

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.6")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "0.5.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.4")
