libraryDependencies <+= (sbtVersion) { sv =>
  "org.scala-sbt" % "scripted-plugin" % sv
}


libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-simple" % "1.7.12",
  "commons-io" % "commons-io" % "2.4"
)

scalacOptions += "-feature"

addSbtPlugin("com.reactific" % "sbt-project" % "1.0.3-SNAPSHOT" )
addSbtPlugin("com.typesafe.sbt"  %% "sbt-osgi" % "0.8.0")
