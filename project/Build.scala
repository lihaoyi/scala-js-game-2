import sbt._
import Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._
import ScalaJSKeys._
import com.lihaoyi.workbench.Plugin.{updateBrowsers, bootSnippet, workbenchSettings}
import org.apache.commons.codec.binary.Base64



object Build extends sbt.Build {

  object Bundle extends sbt.Plugin {
    val bundledDirectory = settingKey[File]("The folder where all to-be-bundled resources comes from")
    val bundleName = settingKey[String]("The final name of the resource bundle")
    val bundleJS = taskKey[Set[File]]("bundles your filesystem tree inside bundleDirectory into a single file")
    val buildSettingsX = Seq(
      watchSources := {
        watchSources.value ++ Path.allSubpaths(bundledDirectory.value).map(_._1).toSeq
      },
      bundledDirectory := (sourceDirectory in Compile).value / "bundled",
      bundleName := "bundled.js",
      bundleJS := {

        val cacheFiles = Path.allSubpaths(bundledDirectory.value).map{ case (file, path) =>
          FileFunction.cached(
            cacheDirectory.value / "bundled" / path,
            FilesInfo.lastModified,
            FilesInfo.exists
          ){(inFiles: Set[File]) =>
            val data = Base64.encodeBase64String(IO.readBytes(inFiles.head)).replaceAll("\\s", "")
            val outFile = cacheDirectory.value / "bundled" / path / "base64data"
            IO.write(outFile, s""""$path": "$data" """)
            Set(outFile)
          }(Set(file))
        }.toSet.flatten

        FileFunction.cached(
          cacheDirectory.value / "totalBundle",
          FilesInfo.lastModified,
          FilesInfo.exists
        ){(inFiles: Set[File]) =>
          val bundle = crossTarget.value / bundleName.value
          val fileLines = inFiles.map(IO.read(_))
          IO.write(bundle, "\nScalaJSBundle = {\n" + fileLines.mkString(",\n") + "\n}" )
          Set(bundle)
        }(cacheFiles)
      }
    )
  }
  lazy val root = project.in(file("."))
    .settings(scalaJSSettings: _*)
    .settings(Bundle.buildSettingsX: _*)
    .settings(workbenchSettings: _*)
    .settings(
      name := "games",
      bootSnippet := "ScalaJS.modules.example_ScalaJSExample().main();",
      (managedSources in packageExportedProductsJS in Compile) := (managedSources in packageExportedProductsJS in Compile).value.filter(_.name.startsWith("00")),
      libraryDependencies += "org.scala-lang.modules.scalajs" %%% "scalajs-dom" % "0.5-SNAPSHOT",
      updateBrowsers <<= updateBrowsers.triggeredBy(packageJS in Compile)
    )

}
