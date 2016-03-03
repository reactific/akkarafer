/**********************************************************************************************************************
  *                                                                                                                    *
  * Copyright (c) 2015, Reactific Software LLC. All Rights Reserved.                                                   *
  *                                                                                                                    *
  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance     *
  * with the License. You may obtain a copy of the License at                                                          *
  *                                                                                                                    *
  *     http://www.apache.org/licenses/LICENSE-2.0                                                                     *
  *                                                                                                                    *
  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed   *
  * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for  *
  * the specific language governing permissions and limitations under the License.                                     *
  **********************************************************************************************************************/


import sbt.Keys._
import sbt._

import com.reactific.sbt.ProjectPlugin
import com.reactific.sbt.ProjectPlugin.identity._
import sbtbuildinfo.BuildInfoKeys._
import scoverage.ScoverageSbtPlugin

/** Main Build Definition For RestOmnia */
object AkkaraferBuild extends Build {

  object Vers {
    val akka = "2.4.2"
    val config = "1.3.0"
    val domino = "1.1.1"
    val karaf = "4.0.4"
  }

  val root_dependencies = Seq(
    "org.apache.karaf" % "apache-karaf" % "4.0.4",
    "com.github.domino-osgi" %% "domino" % Vers.domino,
    "org.ops4j.pax.url" % "pax-url-link" % "2.4.6",
    "org.ops4j.pax.url" % "pax-url-mvn" % "1.3.6",
    "org.ops4j.pax.url" % "pax-url-obr" % "2.4.6",
    "org.ops4j.pax.url" % "pax-url-wrap" % "2.4.6",
    "com.typesafe"      % "config" % Vers.config,
    "com.typesafe.akka" %% "akka-osgi" % Vers.akka,
    "com.typesafe.akka" %% "akka-actor" % Vers.akka,
    "com.typesafe.akka" %% "akka-http-core" % Vers.akka,
    "com.typesafe.akka" %% "akka-http-experimental" % Vers.akka,
    "com.typesafe.akka" %% "akka-testkit" % Vers.akka % "test",
    "com.typesafe.akka" %% "akka-http-testkit" % Vers.akka % "test"
  )

  val classesIgnoredByScoverage : String = Seq[String](
    "<empty>", // Avoids warnings from scoverage
    "com.reactific.akkarafer.BuildInfo"
  ).mkString(";")


  lazy val akkarafer = Project("akkarafer", file(".")).
    enablePlugins(ProjectPlugin).
    settings(
      scalaVersion := "2.11.7",
      organization := "com.reactific",
      titleForDocs := "Akkarafer",
      codePackage := "com.reactific.akkarafer",
      copyrightHolder := "Reactific Software LLC",
      copyrightYears := Seq(2016),
      developerUrl := url("http://reactific.com/"),
      maxErrors := 50,
      buildInfoObject := "BuildInfo",
      buildInfoPackage := "com.reactific.akkarafer",
      ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 90,
      ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageSbtPlugin.ScoverageKeys.coverageExcludedPackages := classesIgnoredByScoverage,
      libraryDependencies ++= root_dependencies
    ).
    aggregate(sbt)

  lazy val sbt = Project("akkarafer-sbt", file("./akkarafer-sbt")).
    enablePlugins(ProjectPlugin).
    settings(
      scalaVersion := "2.10.5",
      organization := "com.reactific",
      titleForDocs := "Akkarafer SBT",
      codePackage := "com.reactific.akkarafer.sbt",
      copyrightHolder := "Reactific Software LLC",
      copyrightYears := Seq(2016),
      developerUrl := url("http://reactific.com/"),
      libraryDependencies ++= Seq(
        ProjectPlugin.pluginModuleID("com.reactific" % "sbt-project" % "0.8.0-SNAPSHOT")
      )
    )

  override def rootProject = Some(akkarafer)

}
