package test

import org.junit.Test
import scala.reflect.io._
import org.junit.Ignore

class CompileScalaLib extends CompilerTest {

  val noCheckOptions = List(
//        "-verbose",
//         "-Ylog:frontend",
//        "-Xprompt",
//        "-explaintypes",
//        "-Yshow-suppressed-errors",
        "-pagewidth", "160")

  implicit val defaultOptions = noCheckOptions ++ List(
      "-Yno-deep-subtypes",
      "-Ycheck:resolveSuper,mixin,restoreScopes"
  )

  val twice = List("#runs", "2", "-YnoDoubleBindings")
  val allowDeepSubtypes = defaultOptions diff List("-Yno-deep-subtypes")

  val scalaDir = "../scala/src/library/scala"

  /** scala file paths relative to one directory deeper than working directory */
  def scalaFiles = Directory(scalaDir).deepFiles.filter(_.extension == "scala").map(f => "../" + f.toString).mkString(" ")

  //@Ignore
  @Test def compileScalaLib = compileDir(scalaDir, "-deep" :: Nil /*, twice*/)(allowDeepSubtypes)
    
  def srewriteCommand: String = {
    val pluginPath = "/home/sam/.ivy2/local/org.scala-lang.plugins/srewriteplugin_2.10/0.1.0/jars/srewriteplugin_2.10.jar"
    val pluginOptions = "-P:srewriteplugin:oversrc"

    "#!/bin/sh\n" +
    // Run plain scalac on scala lib (If we don't add the -Yskip options, we get an infinite loop after or in the flatten phase)
    // s"scalac -Yskip:cleanup,icode,jvm -verbose $scalaFiles\n"
    s"scalac -J-Xmx2g -Yrangepos -Xplugin:$pluginPath $pluginOptions $scalaFiles\n"
  }
  
  @Ignore
  @Test def createSh(): Unit = {
    val dir = Directory("./srewrite")
    dir.createDirectory(failIfExists = false)
    val path = dir / "run-srewrite.sh"
    println(s"Writing srewrite command to file $path")
    val f = File(path)
    f.writeAll(srewriteCommand)
    f.setExecutable(true, ownerOnly = false)
  }
}
