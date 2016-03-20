
import java.io.File
import java.io._
import java.util.zip.GZIPInputStream

import com.reactific.sbt.AutoPluginHelper
import org.apache.commons.compress.archivers.tar.{TarArchiveInputStream, TarArchiveEntry}
import org.apache.commons.io.input.BoundedInputStream
import sbt.Keys._
import sbt.Using._
import sbt._
import com.typesafe.sbt.packager.jdkpackager.JDKPackagerPlugin
import com.typesafe.sbt.packager.jdkpackager.JDKPackagerPlugin.autoImport._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal


import scala.xml.{Utility, TopScope, Elem}

sealed trait FeatureItem {
  def genXML : Elem
}

case class Config(pid: String, items: Map[String,String]) extends FeatureItem {
  def genXML : Elem = {
    <config name={pid}>
      {items.map(x=> s"${x._1} = ${x._2}").mkString("\n")}
    </config>
  }
}

case class Bundle(url: String, startLevel: Int = 50, start: Boolean = true, dependency: Boolean = false)
  extends FeatureItem
{
  def genXML : Elem = {
    <bundle start-level={startLevel.toString} start={start.toString} dependency={dependency.toString}>{url}</bundle>
  }
}

case class FeatureRef(name: String, version: Option[String]) extends FeatureItem {
  def genXML : Elem = {
    version match {
      case Some(v) ⇒
        <feature version={v}>{name}</feature>
      case None ⇒
        <feature>{name}</feature>
    }
  }
}

case class Feature(
  name : String,
  version: String,
  description: Option[String],
  installOnBoot : Boolean = true,
  bundles: Seq[Bundle] = Seq.empty[Bundle],
  configuration: Seq[Config] = Seq.empty[Config],
  dependentFeatures: Seq[FeatureRef] = Seq.empty[FeatureRef]
) extends FeatureItem {
  def genXML : Elem = {
    <feature name={name} description={description.getOrElse("")} version={version} resolver="(obr)">{
      for ( df <- dependentFeatures) yield { df.genXML }
      for ( bun <- bundles) yield { bun.genXML }
      for (conf <- configuration) yield { conf.genXML }
      }</feature>
  }
}

case class Features(name : String, features: Seq[Feature]) extends FeatureItem {
  def genXML : Elem = {
    <features name={name} xmlns="http://karaf.apache.org/xmlns/features/v1.3.0">{
      for (feature <- features) yield { feature.genXML }
      }</features>
  }

  override def toString : String = {
    val sb = new StringBuilder(4096)
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
    Utility.serialize(genXML, TopScope, sb, stripComments = true, decodeEntities = false, preserveWhitespace = true)
    sb.toString()
  }
}

/** Unit Tests For Karaf */
object Karaf extends AutoPluginHelper {

  def autoPlugins : Seq[AutoPlugin] = Seq(JDKPackagerPlugin)

  object autoImport {

    val karaf_tgz_url = settingKey[URL]("The URL from which the Karaf .tgz distribution should be downloaded.")

    val features = settingKey[Seq[Features]]("The Karaf features repositories to generate")

    val bootFeatures = settingKey[Seq[String]]("The pre-defined Karaf features to boot up with")

    val featuresTask = TaskKey[Seq[File]]("gen-features",
      "The task to generate the feature repositories and configuration files")

    val downloadKarafTask = TaskKey[Seq[(File,String)]]("download-karaf",
      "The task to download and unpack the Karaf distribution")
  }

  import autoImport._

  val osgi_version = "5.0.0"
  val scala_version = "2.11.7"
  val typesafe_config_version = "1.3.0"
  val akka_version = "2.4.2"

  val akkaFeatures = Features("akka", Seq[Feature](
    Feature(name="osgi-compendium", description=Some("OSGi compendium feature"), version="${osgi.version}",
      bundles = Seq(
        Bundle(url="mvn:org.osgi/org.osgi.compendium/${osgi.version}", startLevel=10, start=true)
      )
    ),
    Feature(name="scala", description=Some("Scala"), version="${scala_version}", bundles=Seq(
      Bundle(startLevel=15, url="mvn:org.scala-lang/scala-library/${scala.version}"),
      Bundle(startLevel=15, url="mvn:org.scala-lang/scala-reflect/${scala.version}")
      )
    ),
    Feature(name="typesafe-config", description=Some("Typesafe config"), version="${typesafe_config_version}",
      bundles=Seq(
        Bundle(startLevel=25, url="mvn:com.typesafe/config/${typesafe.config.version}")
      )
    ),
    Feature(name="akka", description=Some("Akka"), version="${akka_version}",
      bundles=Seq(
        Bundle(startLevel=30, url="mvn:com.typesafe.akka/akka-actor_${scala_dep_version}/${akka_version}"),
        Bundle(startLevel=30, url="mvn:com.typesafe.akka/akka-osgi_${scala_dep_version}/${akka_version}"),
        Bundle(startLevel=30, url="mvn:com.typesafe.akka/akka-slf4j_${scala_dep_version}/${akka_version}"),
        Bundle(startLevel=30, url="mvn:com.typesafe.akka/akka-http-core_${scala_dep_version}/${akka_version}"),
        Bundle(startLevel=30, url="mvn:com.typesafe.akka/akka-http-experimental_${scala_dep_version}/${akka_version}")
      ),
      dependentFeatures = Seq(
        FeatureRef("scala", Some(scala_version)), FeatureRef("typesafe-config", Some(typesafe_config_version))
      )
    )
  ))

  /** The [[sbt.Setting]]s to add in the scope of each project that activates this AutoPlugin. */
  override def projectSettings: Seq[Setting[_]] = Seq(
    karaf_tgz_url := new URL("http://apache.arvixe.com/karaf/4.0.4/apache-karaf-4.0.4.tar.gz"),
    features := Seq.empty[Features],
    bootFeatures := Seq("instance", "package", "log", "ssh", "aries-blueprint", "system", "feature", "shell",
      "management", "service", "jaas", "shell-compat", "deployer", "diagnostic", "wrap", "bundle", "config",
      "kar"),
    featuresTask <<= features_task,
    downloadKarafTask <<= download_karaf_task,
    resourceGenerators in Compile <+= features_task,
    mappings in Universal ++= download_karaf_task.value,

    jdkPackagerBasename := name.value,
    jdkPackagerType := "all",
    jdkPackagerJVMArgs := Seq("-Xmx1g"),
    packageSummary := "Akka+Karaf",
    packageDescription := "A package containing Akka and Apache Karaf, well integrated.",
    mainClass in Compile := Some("org.apache.karaf.main.Main")
  )

  def dropFirstDir(str: String) = {
    str.split('/').drop(1).mkString("/")
  }

  def downloadAndUnpackTarFile(url :URL, dest: File) : Seq[(File,String)] = {
    urlInputStream(url) { in ⇒
      gzipInputStream(in) { gzStream : GZIPInputStream ⇒
        val tarInput = new TarArchiveInputStream(gzStream)
        dest.mkdirs()

        require(dest.isDirectory, s"Destination ${dest.getCanonicalPath} for unpacking Karaf TGZ must be a directory")
        var entry : TarArchiveEntry = tarInput.getNextTarEntry
        var result = Seq.empty[(File,String)]
        while(entry != null) {
          val entryName = dropFirstDir(entry.getName)
          if (entry.isDirectory) {
            val dir = new File(dest, entryName)
            require(dir.mkdirs(), s"Unable to make directory: $entryName")
          } else {
            val bis = new BoundedInputStream(tarInput, entry.getSize)
            val outFile = new File(dest, entryName)
            outFile.getParentFile.mkdirs()
            val outputFile = new FileOutputStream(outFile)
            IO.transfer(bis, outputFile)
            outputFile.close()
            result :+= (outFile → entryName)
          }
          entry = tarInput.getNextTarEntry
        }
        result
      }
    }
  }

  def flattenFile(file : File) : Seq[File] = {
    if (file.isDirectory) {
      file.listFiles.flatMap { f =>
        flattenFile(f)
      } toSeq
    } else {
      Seq(file)
    }
  }


  def download_karaf_task = {
    (karaf_tgz_url, resourceManaged in Compile) map { (url, outDir) ⇒
      outDir.mkdirs()
      if (!outDir.exists || !outDir.isDirectory || outDir.list().isEmpty) {
        println(s"${outDir.getCanonicalPath} is empty, downloading from ${url.toExternalForm}")
        downloadAndUnpackTarFile(url, outDir)
      } else {
         val files : Seq[(File,String)] = flattenFile(outDir) pair relativeTo(outDir)
        println(s"${outDir.getCanonicalPath} is not empty, no need to download, found ${files.size} files")
        files
      }
    }
  }

  def features_task = {
    (features, bootFeatures, resourceManaged in Compile) map { (features, boots, outDir) ⇒
      val features_files = for (f <- features) yield {
        val feature_file = new File(outDir, s"features/${f.name}.xml")
        val fos = new PrintStream(feature_file)
        try {
          fos.print(features.toString)
        } finally {
          fos.close()
        }
        feature_file
      }
      val features_repos_file = new File(outDir, s"etc/org.apache.karaf.features.repos.cfg")
      val ps = new PrintStream(features_repos_file)
      try {
        ps.print(
          s"""#
              |# This file names the feature repository XML files by assigning a URL to a name.
              |# The file is used with the feature:repo-* commands to specify repositories in
              |# a concise manner.
              |#
              |# Pre-defined Public Repositories Of Features from Karaf, Camel, ops4j, and others.
              |enterprise=mvn:org.apache.karaf.features/enterprise/LATEST/xml/features
              |spring=mvn:org.apache.karaf.features/spring/LATEST/xml/features
              |cellar=mvn:org.apache.karaf.cellar/apache-karaf-cellar/LATEST/xml/features
              |cave=mvn:org.apache.karaf.cave/apache-karaf-cave/LATEST/xml/features
              |camel=mvn:org.apache.camel.karaf/apache-camel/LATEST/xml/features
              |camel-extras=mvn:org.apache-extras.camel-extra.karaf/camel-extra/LATEST/xml/features
              |cxf=mvn:org.apache.cxf.karaf/apache-cxf/LATEST/xml/features
              |cxf-dosgi=mvn:org.apache.cxf.dosgi/cxf-dosgi/LATEST/xml/features
              |cxf-xkms=mvn:org.apache.cxf.services.xkms/cxf-services-xkms-features/LATEST/xml
              |activemq=mvn:org.apache.activemq/activemq-karaf/LATEST/xml/features
              |jclouds=mvn:org.apache.jclouds.karaf/jclouds-karaf/LATEST/xml/features
              |openejb=mvn:org.apache.openejb/openejb-feature/LATEST/xml/features
              |wicket=mvn:org.ops4j.pax.wicket/features/LATEST/xml/features
              |hawtio=mvn:io.hawt/hawtio-karaf/LATEST/xml/features
              |pax-cdi=mvn:org.ops4j.pax.cdi/pax-cdi-features/LATEST/xml/features
              |pax-jdbc=mvn:org.ops4j.pax.jdbc/pax-jdbc-features/LATEST/xml/features
              |pax-jpa=mvn:org.ops4j.pax.jpa/pax-jpa-features/LATEST/xml/features
              |pax-web=mvn:org.ops4j.pax.web/pax-web-features/LATEST/xml/features
              |pax-wicket=mvn:org.ops4j.pax.wicket/pax-wicket-features/LATEST/xml/features
              |ecf=http://download.eclipse.org/rt/ecf/latest/site.p2/karaf-features.xml
              |decanter=mvn:org.apache.karaf.decanter/apache-karaf-decanter/LATEST/xml/features
              |eclipsesource-jaxrs=mvn:com.eclipsesource.jaxrs/features/LATEST/xml/features
              |aries-jpa=mvn:org.apache.aries.jpa/jpa-features/LATEST/xml/features
              |hibernate=mvn:org.hibernate/hibernate-osgi/LATEST/xml/karaf
              |ignite=mvn:org.apache.ignite/ignite-osgi-karaf/LATEST/xml/features
              |artemis=mvn:org.apache.activemq/artemis-features/LATEST/xml
              |
          |# Local Feature Repositories
        """.stripMargin
        )
        for (f ← features) {
          ps.print(f.name)
          ps.print("=")
          ps.println(s"file:features/${f.name}.xml")
        }
      } finally {
        ps.close()
      }
      val features_config_file = new File(outDir, s"etc/org.apache.karaf.features.cfg")
      val fc = new PrintStream(features_config_file)
      try {
        fc.print(
          """#
            |# Comma separated list of features repositories to register by default
            |#
            |featuresRepositories = \
            |    mvn:org.apache.karaf.features/base/4.0.x/xml/features, \
            |    mvn:org.apache.karaf.features/enterprise/4.0.x/xml/features, \
            |    mvn:org.apache.karaf.features/framework/4.0.x/xml/features, \
            |    mvn:org.apache.karaf.features/spring/4.0.x/xml/features, \
            |    mvn:org.apache.karaf.features/standard/4.0.x/xml/features, \
            |    mvn:org.apache.karaf.features/static/4.0.x/xml/features, \
            |#
            |# Comma separated list of features to install at startup
            |#
            |featuresBoot =
          """.stripMargin
        )
        val bootList = boots ++ features.map{_.name} mkString ", "
        fc.println(bootList)
        fc.print(
          """
            |#
            |# Resource repositories (OBR) that the features resolver can use
            |# to resolve requirements/capabilities
            |#
            |# The format of the resourceRepositories is
            |# resourceRepositories=[xml:url|json:url],...
            |# for Instance:
            |#
            |#resourceRepositories=xml:http://host/path/to/index.xml
            |# or
            |#resourceRepositories=json:http://host/path/to/index.json
            |#
            |
            |#
            |# Defines if the boot features are started in asynchronous mode (in a dedicated thread)
            |#
            |featuresBootAsynchronous=false
            |
            |#
            |# Service requirements enforcement
            |#
            |# By default, the feature resolver checks the service requirements/capabilities of
            |# bundles for new features (xml schema >= 1.3.0) in order to automatically installs
            |# the required bundles.
            |# The following flag can have those values:
            |#   - disable: service requirements are completely ignored
            |#   - default: service requirements are ignored for old features
            |#   - enforce: service requirements are always verified
            |#
            |#serviceRequirements=default
          """.stripMargin
        )
      }
      features_files ++ Seq[File](features_config_file, features_repos_file)
    }
  }

}
