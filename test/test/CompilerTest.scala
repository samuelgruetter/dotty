package test

import scala.reflect.io._
import org.junit.Test
import scala.collection.mutable.ListBuffer
import dotty.tools.dotc.{Main, Bench, Driver}
import dotty.tools.dotc.reporting.Reporter

class CompilerTest extends DottyTest {

  def defaultOptions: List[String] = Nil

  def compileArgs(args: Array[String], xerrors: Int = 0): Unit = {
    val allArgs = args ++ defaultOptions
    val processor = if (allArgs.exists(_.startsWith("#"))) Bench else Main
    val nerrors = processor.process(allArgs).count(Reporter.ERROR.level)
    assert(nerrors == xerrors, s"Wrong # of errors. Expected: $xerrors, found: $nerrors")
  }

  def compileLine(cmdLine: String, xerrors: Int = 0): Unit = compileArgs(cmdLine.split("\n"), xerrors)

  def compileFile(prefix: String, fileName: String, args: List[String] = Nil, xerrors: Int = 0): Unit =
    compileArgs((s"$prefix$fileName.scala" :: args).toArray, xerrors)

  def compileDir(path: String, args: List[String] = Nil, xerrors: Int = 0): Unit = {
    val dir = Directory(path)
    val fileNames = dir.files.toArray.map(_.toString).filter(_ endsWith ".scala")
    compileArgs(fileNames ++ args, xerrors)
  }

}
object CompilerText extends App {

//  val dotcDir = "/Users/odersky/workspace/dotty/src/dotty/"

//  new CompilerTest().compileFile(dotcDir + "tools/dotc/", "CompilationUnit")
//  new CompilerTest().compileFile(dotcDir + "tools/dotc/", "Compiler")
//  new CompilerTest().compileFile(dotcDir + "tools/dotc/", "Driver")
//  new CompilerTest().compileFile(dotcDir + "tools/dotc/", "Main")
//  new CompilerTest().compileFile(dotcDir + "tools/dotc/", "Run")

//  new CompilerTest().compileDir(dotcDir + "tools/dotc")
 // new CompilerTest().compileFile(dotcDir + "tools/dotc/", "Run")
}