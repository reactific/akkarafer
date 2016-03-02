/**********************************************************************************************************************
  * This file is part of Scrupal, a Scalable Reactive Web Application Framework for Content Management                 *
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

package com.reactific.akkarafer

import com.reactific.sbt.{SubProjectPluginTrait, ProjectPluginTrait}

import sbt.Keys._
import sbt._

case class Config(pid: String, items: Map[String,String])
case class Bundle(url: String, startLevel: Int = 50, start: Boolean = true)
case class FeatureRef(name: String, version: Option[String])
case class Feature(
    name : String,
    version: String,
    description: Option[String],
    installOnBoot : Boolean = true,
    bundles: Seq[Bundle] = Seq.empty[Bundle],
    configuration: Seq[Config] = Seq.empty[Config],
    dependentFeatures: Seq[FeatureRef] = Seq.empty[FeatureRef]
) {
  def genXML(file : File) : Unit = {

  }
}

/** An SBT Plugin For Akka/Karaf Projects */
object AkkaraferPlugin extends ProjectPluginTrait {

  object branding {
    val welcome = settingKey[String]("The welcome banner to display in the console per your branding.")
    val prompt = settingKey[String]("The console prompt for your branding.")
  }

  object configuration {
    val features = settingKey[Feature]("The features to be ")
  }

  /**
    * Define the values of the settings
    */
  override def projectSettings: Seq[Setting[_]] = {

    object Vers {
      val akka = "2.4.2"
      val config = "1.3.0"
      val pax = "2.4.6"
      val domino = "1.1.1"
      val karaf = "4.0.4"
    }

    super.projectSettings ++ Seq (
      libraryDependencies ++= Seq(
        "org.apache.karaf" % "apache-karaf" % Vers.karaf,
        "com.github.domino-osgi" %% "domino" % Vers.domino,
        "org.ops4j.pax.url" % "pax-url-link" % Vers.pax,
        "org.ops4j.pax.url" % "pax-url-mvn" % Vers.pax,
        "org.ops4j.pax.url" % "pax-url-obr" % Vers.pax,
        "org.ops4j.pax.url" % "pax-url-wrap" % Vers.pax,
        "com.typesafe"      % "config" % Vers.config,
        "com.typesafe.akka" %% "akka-osgi" % Vers.akka,
        "com.typesafe.akka" %% "akka-actor" % Vers.akka,
        "com.typesafe.akka" %% "akka-http-core" % Vers.akka,
        "com.typesafe.akka" %% "akka-http-experimental" % Vers.akka,
        "com.typesafe.akka" %% "akka-testkit" % Vers.akka % "test",
        "com.typesafe.akka" %% "akka-http-testkit" % Vers.akka % "test"
      )
    )
  }
}






















