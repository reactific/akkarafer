
import java.io.File
import java.io._
import java.util.zip.ZipInputStream

import com.reactific.sbt.AutoPluginHelper
import org.apache.commons.io.input.BoundedInputStream
import sbt.Keys._
import sbt.Using._
import sbt._
import com.typesafe.sbt.packager.jdkpackager.JDKPackagerPlugin
import com.typesafe.sbt.packager.jdkpackager.JDKPackagerPlugin.autoImport._
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal

import scala.language.postfixOps

import scala.xml._

sealed trait FeatureItem {
  def genXML : Elem
}

case class Repository(url: String) extends FeatureItem {
  def genXML : Elem = {
    <repository>{url}</repository>
  }
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
    <bundle start-level={startLevel.toString} start={start.toString} dependency={dependency.toString}>
      {url}
    </bundle>

  }
}

case class FeatureRef(name: String, version: Option[String]) extends FeatureItem {
  def genXML : Elem = {
    version match {
      case Some(v) ⇒
        <feature version={v}>
          {name}
        </feature>

      case None ⇒
        <feature>
          {name}
        </feature>

    }
  }
}

case class Feature(
  name : String,
  version: String,
  description: Option[String],
  installOnBoot : Boolean = true,
  repositories: Seq[Repository] = Seq.empty[Repository],
  bundles: Seq[Bundle] = Seq.empty[Bundle],
  configurations: Seq[Config] = Seq.empty[Config],
  dependentFeatures: Seq[FeatureRef] = Seq.empty[FeatureRef]
) extends FeatureItem {
  def genXML : Elem = {
    val dfs: NodeSeq = for ( df <- dependentFeatures) yield { df.genXML }
    val buns: NodeSeq = for ( bun <- bundles) yield { bun.genXML }
    val cofns: NodeSeq = for (conf <- configurations) yield { conf.genXML }
    <feature name={name} description={description.getOrElse("")} version={version} resolver="(obr)">
      {dfs}
      {buns}
      {cofns}
    </feature>
  }
}

case class Features(name : String, features: Seq[Feature]) extends FeatureItem {
  def genXML : Elem = {
    <features name={name}
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xmlns="http://karaf.apache.org/xmlns/features/v1.3.0"
      xsi:schemaLocation="http://karaf.apache.org/xmlns/features/v1.3.0 http://karaf.apache.org/xmlns/features/v1.3.0">
      {
      for (feature <- features) yield {
        feature.genXML
      }
      }
    </features>
  }

  override def toString : String = {
    val sb = new StringBuilder(4096)
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    Utility.serialize(genXML, TopScope, sb, stripComments = true, decodeEntities = false,
      preserveWhitespace = false,
      minimizeTags = MinimizeMode.Always)
    sb.toString()
  }
}

/** Unit Tests For Karaf */
object Karaf extends AutoPluginHelper {

  def autoPlugins : Seq[AutoPlugin] = Seq(JDKPackagerPlugin)

  object autoImport {

    val karaf_zip_url = settingKey[URL]("The URL from which the Karaf .tgz distribution should be downloaded.")

    val features = settingKey[Seq[Features]]("The Karaf features repositories to generate")

    val bootFeatures = settingKey[Seq[String]]("The pre-defined Karaf features to boot up with")

    val featuresTask = TaskKey[Seq[File]]("gen-features",
      "The task to generate the feature repositories and configuration files")

    val downloadKarafTask = TaskKey[Seq[(File,String)]]("download-karaf",
      "The task to download and unpack the Karaf distribution")
  }

  import autoImport._

  object ver {
    val osgi = "5.0.0"
    def scala_dep(n : String) = s"2.11/$n"
    val scala = "2.11.7"
    object typesafe {
      val config = "1.3.0"
      val akka = "2.4.4"
    }
    val karaf = "4.0.5"
    val cellar = "4.0.0"
    val jclouds = "1.9.1"
    val hazelcast = "3.6.0"
    val joda_time = "2.9.1"
    object felix {
      val bundlerepository = "2.0.6"
      val utils = "1.8.0"
      val webconole = "4.2.14"
    }
    /*
      <fabric8.version>2.2.23</fabric8.version>
      <fabric8.kubernetes-client.version>1.3.22</fabric8.kubernetes-client.version>
      <felix.bundlerepository.version>2.0.6</felix.bundlerepository.version>
      <felix.utils.version>1.8.0</felix.utils.version>
      <felix.webconsole.version>4.2.14</felix.webconsole.version>
      <hazelcast.version>3.5.4</hazelcast.version>
      <jclouds.version>1.9.1</jclouds.version>
      <joda-time.version>2.9.1</joda-time.version>
      <junit.version>4.11</junit.version>
      <karaf.version>4.0.3</karaf.version>
      <osgi.version>6.0.0</osgi.version>
      <osgi.compendium.version>5.0.0</osgi.compendium.version>
      <slf4j.version>1.7.7</slf4j.version>
      <bnd.version.policy>[$(version;==;$(@)),$(version;+;$(@)))</bnd.version.policy>
*/
  }

  val akkaFeatures = Features(s"akka-${ver.typesafe.akka}", Seq[Feature](
    Feature(name="osgi-compendium", description=Some("OSGi compendium feature"), version=s"${ver.osgi}",
      bundles = Seq(
        Bundle(url=s"mvn:org.osgi/org.osgi.compendium/${ver.osgi}", startLevel=10, start=true)
      )
    ),
    Feature(name="scala", description=Some("Scala"), version=s"${ver.scala}", bundles=Seq(
      Bundle(startLevel=15, url=s"mvn:org.scala-lang/scala-library/${ver.scala}"),
      Bundle(startLevel=15, url=s"mvn:org.scala-lang/scala-reflect/${ver.scala}")
      )
    ),
    Feature(name="typesafe-config", description=Some("Typesafe config"), version=s"${ver.typesafe.config}",
      bundles=Seq(
        Bundle(startLevel=25, url=s"mvn:com.typesafe/config/${ver.typesafe.config}")
      )
    ),
    Feature(name="akka", description=Some("Akka"), version=s"${ver.typesafe.akka}",
      bundles=Seq(
        Bundle(startLevel=30, url=s"mvn:com.typesafe.akka/akka-actor_${ver.scala_dep(ver.typesafe.akka)}"),
        Bundle(startLevel=30, url=s"mvn:com.typesafe.akka/akka-osgi_${ver.scala_dep(ver.typesafe.akka)}"),
        Bundle(startLevel=30, url=s"mvn:com.typesafe.akka/akka-slf4j_${ver.scala_dep(ver.typesafe.akka)}"),
        Bundle(startLevel=30, url=s"mvn:com.typesafe.akka/akka-http-core_${ver.scala_dep(ver.typesafe.akka)}"),
        Bundle(startLevel=30, url=s"mvn:com.typesafe.akka/akka-http-experimental_${ver.scala_dep(ver.typesafe.akka)}")
      ),
      dependentFeatures = Seq(
        FeatureRef("scala", Some(ver.scala)), FeatureRef("typesafe-config", Some(ver.typesafe.config))
      )
    )
  ))

  val cellarFeatures = Features("cellar", features=Seq[Feature](
    Feature(
      name=s"karaf-cellar-${ver.cellar}",
      version="${ver.cellar}",
      description=Some("Apache Karaf Cellar: Distributed/Clustered OSGi Container"),
      installOnBoot = true,
      repositories = Seq[Repository](
        Repository("mvn:org.apache.jclouds.karaf/jclouds-karaf/${jclouds.version}/xml/features")
      ),
      bundles = Seq[Bundle](),
      configurations = Seq[Config](),
      dependentFeatures = Seq[FeatureRef]()
    )
  ))

  val foo=  """
      |<features name="karaf-cellar-${project.version}" xmlns="http://karaf.apache.org/xmlns/features/v1.3.0"
      |xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://karaf.apache
      |.org/xmlns/features/v1.3.0 http://karaf.apache.org/xmlns/features/v1.3.0">
      |
      |    <repository>mvn:org.apache.jclouds.karaf/jclouds-karaf/${jclouds.version}/xml/features</repository>
      |    <repository>mvn:io.fabric8/karaf-features/${fabric8.version}/xml/features</repository>
      |
      |    <feature name="cellar-core" description="Karaf clustering core" version="${project.version}">
      |        <feature>aries-proxy</feature>
      |        <feature>shell</feature>
      |        <configfile finalname="/etc/org.apache.karaf.cellar.groups.cfg">mvn:org.apache.karaf
      |        .cellar/apache-karaf-cellar/${project.version}/cfg/groups</configfile>
      |        <configfile finalname="/etc/org.apache.karaf.cellar.node.cfg">mvn:org.apache.karaf
      |        .cellar/apache-karaf-cellar/${project.version}/cfg/node</configfile>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.core/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="hazelcast" description="In memory data grid" version="${hazelcast.version}">
      |        <configfile finalname="/etc/hazelcast.xml">mvn:org.apache.karaf.cellar/apache-karaf-cellar/${project
      |        .version}/xml/hazelcast</configfile>
      |        <bundle>mvn:org.apache.geronimo.specs/geronimo-jta_1.1_spec/1.1.1</bundle>
      |        <bundle>mvn:com.eclipsesource.minimal-json/minimal-json/0.9.2</bundle>
      |        <bundle>mvn:com.hazelcast/hazelcast-all/${hazelcast.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-hazelcast" description="Cellar implementation based on Hazelcast" version="${project
      |    .version}">
      |        <feature version="${hazelcast.version}">hazelcast</feature>
      |        <feature version="${project.version}">cellar-core</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.hazelcast/${project.version}</bundle>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.utils/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-config" description="ConfigAdmin cluster support" version="${project.version}">
      |        <feature>config</feature>
      |        <feature>cellar-hazelcast</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.config/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-features" description="Karaf features cluster support" version="${project.version}">
      |        <feature>feature</feature>
      |        <feature>cellar-hazelcast</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.features/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-bundle" description="Bundle cluster support" version="${project.version}">
      |        <feature>bundle</feature>
      |        <feature>cellar-hazelcast</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.bundle/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-shell" description="Cellar shell support" version="${project.version}">
      |        <feature>shell</feature>
      |        <feature>cellar-hazelcast</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.shell/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar" description="Karaf clustering" version="${project.version}">
      |        <feature>cellar-hazelcast</feature>
      |        <feature>cellar-shell</feature>
      |        <feature>cellar-config</feature>
      |        <feature>cellar-bundle</feature>
      |        <feature>cellar-features</feature>
      |    </feature>
      |
      |    <feature name="cellar-dosgi" description="DOSGi support" version="${project.version}">
      |        <feature>cellar-hazelcast</feature>
      |        <bundle start-level="40">mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.dosgi/${project
      |        .version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-obr" description="OBR cluster support" version="${project.version}">
      |        <feature>obr</feature>
      |        <feature>cellar-hazelcast</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.obr/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-eventadmin" description="OSGi events broadcasting in clusters" version="${project
      |    .version}">
      |        <feature>eventadmin</feature>
      |        <feature>cellar-hazelcast</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.event/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-cloud" description="Cloud blobstore support in clusters" version="${project.version}">
      |        <feature>cellar-hazelcast</feature>
      |        <feature version="${jclouds.version}">jclouds</feature>
      |        <!-- Adding S3 as the default Blobstore -->
      |        <feature>jclouds-aws-s3</feature>
      |        <bundle>mvn:joda-time/joda-time/${joda-time.version}</bundle>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.cloud/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-kubernetes" description="Cellar kubernetes support in clusters" version="${project
      |    .version}">
      |        <feature>fabric8-kubernetes-api</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.kubernetes/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-webconsole" description="Cellar plugin for Karaf WebConsole" version="${project
      |    .version}">
      |        <feature>webconsole</feature>
      |        <feature>cellar-hazelcast</feature>
      |        <bundle>mvn:org.apache.karaf.cellar/org.apache.karaf.cellar.webconsole/${project.version}</bundle>
      |    </feature>
      |
      |    <feature name="cellar-http-balancer" description="Cellar HTTP request balancer" version="${project.version}">
      |        <feature>cellar-hazelcast</feature>
      |        <feature>http</feature>
      |        <feature>http-whiteboard</feature>
      |        <bundle>mvn:org.apache.karaf.cellar.http/org.apache.karaf.cellar.http.balancer/${project
      |        .version}</bundle>
      |    </feature>
      |
      |</features>
      |
    """.stripMargin

  /** The [[sbt.Setting]]s to add in the scope of each project that activates this AutoPlugin. */
  override def projectSettings: Seq[Setting[_]] = Seq(
    karaf_zip_url := url(
      s"http://mirror.reverse.net/pub/apache/karaf/${ver.karaf}/apache-karaf-${ver.karaf}.zip"),
    features := Seq[Features](
      akkaFeatures
    ),
    bootFeatures := Seq("instance", "package", "log", "ssh", "aries-blueprint", "system", "feature", "shell",
      "management", "service", "jaas", "shell-compat", "deployer", "diagnostic", "wrap", "bundle", "config",
      "kar"),
    featuresTask <<= features_task,
    downloadKarafTask <<= download_karaf_zip_task,
    mappings in Universal ++= download_karaf_zip_task.value,
    resourceGenerators in Compile <+= features_task,

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

  def downloadAndUnpackZipFile(url : URL, dest : File) : Seq[(File,String)] = {
    require(dest.isDirectory, s"Destination ${dest.getCanonicalPath} for unpacking Karaf ZIP must be a directory")
    urlInputStream(url) { in ⇒
      zipInputStream(in) { zipStream : ZipInputStream ⇒
        var entry = zipStream.getNextEntry
        var result = Seq.empty[(File,String)]
        while (entry != null) {
          val entryName = dropFirstDir(entry.getName)
          if (entry.isDirectory) {
            println(s"Directory: $entryName")
            val dir = new File(dest, entryName)
            require(dir.mkdirs(), s"Unable to make directory: $entryName")
          } else {
            val bis = new BoundedInputStream(zipStream, entry.getSize)
            val outFile = new File(dest, entryName)
            outFile.getParentFile.mkdirs()
            val outputFile = new FileOutputStream(outFile)
            IO.transfer(bis, outputFile)
            outputFile.close()
            result :+= (outFile → entryName)
          }
          entry = zipStream.getNextEntry
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


  def download_karaf_zip_task = {
    (karaf_zip_url, resourceManaged in Compile) map { (url, outDir) ⇒
      outDir.mkdirs()
      if (!outDir.exists || !outDir.isDirectory || outDir.list().isEmpty) {
        println(s"${outDir.getCanonicalPath} is empty, downloading from ${url.toExternalForm}")
        downloadAndUnpackZipFile(url, outDir)
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
        feature_file.getParentFile.mkdirs()
        val fos = new PrintStream(feature_file)
        try {
          fos.print(f.toString)
        } finally {
          fos.close()
        }
        feature_file
      }
      val features_repos_file = new File(outDir, s"etc/org.apache.karaf.features.repos.cfg")
      features_repos_file.getParentFile.mkdirs()
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
          |""".stripMargin
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
