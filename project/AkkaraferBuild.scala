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


import com.typesafe.sbt.pgp.PgpKeys
import sbt.Keys._
import sbt._
import sbt.mavenint.PomExtraDependencyAttributes
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Version.Bump.Next
import xerial.sbt.Sonatype

/** Main Build Definition For RestOmnia */
object AkkaraferBuild extends Build {

  lazy val akkarafer_resolvers = Seq(
    "BinTray-sbt" at "https://dl.bintray.com/sbt/sbt-plugin-releases",
    "BinTray-Typesafe" at "https://dl.bintray.com/typesafe/ivy-releases",
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
    "jgit-repo" at "http://download.eclipse.org/jgit/maven"
  )

  val sonatype = publishTo :=
    Some("Sonatype Snapshots Nexus" at "https://oss.sonatype.org/content/repositories/snapshots")

  lazy val akkarafer = Project("akkarafer", file("."))
    .enablePlugins(Sonatype)
    .settings(ScriptedPlugin.scriptedSettings)
    .settings(
      sbtPlugin       := true,
      organization    := "com.reactific",
      scalaVersion    := "2.10.5",
      scalacOptions   ++= Seq("-deprecation", "-unchecked", "-feature", "-Xlint"),
      logLevel        := Level.Info,
      resolvers       ++= akkarafer_resolvers,
      // Scripted - sbt plugin tests
      ScriptedPlugin.scriptedLaunchOpts := {
        ScriptedPlugin.scriptedLaunchOpts.value ++ Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
      },
      ScriptedPlugin.scriptedBufferLog := false,

      // Release process
      releaseUseGlobalVersion := true,
      releaseVersionBump := Next,
      releasePublishArtifactsAction := PgpKeys.publishSigned.value,
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runClean,
        runTest,
        releaseStepCommand("scripted"),
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        releaseStepCommand("packageBin"),
        publishArtifacts,
        setNextVersion,
        commitNextVersion,
        ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
        pushChanges
      ),

      // Libraries for the project we plug into
      libraryDependencies ++= Seq (
        "org.slf4j" % "slf4j-simple" % "1.7.12",
        pluginModuleID("com.reactific" % "sbt-project" % "0.8.0-SNAPSHOT"),
        pluginModuleID("com.typesafe.sbt" % "sbt-osgi" % "0.8.0")
      ),

      // Publishing to sonatype
      Sonatype.SonatypeKeys.sonatypeProfileName := "org.scrupal",
      publishMavenStyle := true,
      publishArtifact in Test := false,
      pomIncludeRepository := { _ => false },
      licenses := Seq("Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
      homepage := Some(new URL("https://github.com/scrupal/" + normalizedName.value + ".git")),
      pomExtra in Global := {
        <scm>
          <url>git@github.com:scrupal/scrupal-sbt.git</url>
          <connection>scm:git:git@github.com:scrupal/scrupal-sbt.git</connection>
        </scm>
          <developers>
            <developer>
              <id>reid-spencer</id>
              <name>Reid Spencer</name>
              <url>https://github.com/reid-spencer</url>
            </developer>
          </developers>
      }
    )

  override def rootProject = Some(akkarafer)

  val scalaV = "2.10"
  val sbtV = "0.13"

  def pluginModuleID(m: ModuleID) : ModuleID = {
    m.extra(PomExtraDependencyAttributes.SbtVersionKey -> sbtV,
      PomExtraDependencyAttributes.ScalaVersionKey -> scalaV)
      .copy(crossVersion = CrossVersion.Disabled)
  }

}
