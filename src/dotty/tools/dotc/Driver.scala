package dotty.tools.dotc

import config.CompilerCommand
import core.Contexts.{Context, ContextBase}
import util.DotClass
import reporting._

abstract class Driver extends DotClass {

  val prompt = "\ndotc>"

  protected def newCompiler(): Compiler

  protected def emptyReporter: Reporter = new StoreReporter

  protected def doCompile(compiler: Compiler, fileNames: List[String])(implicit ctx: Context): Reporter =
    if (fileNames.nonEmpty) {
      val run = compiler.newRun
      run.compile(fileNames)
      run.printSummary()
    } else emptyReporter

  protected def initCtx = (new ContextBase).initialCtx

  def process(args: Array[String]): Reporter = {
    val summary = CompilerCommand.distill(args)(initCtx)
    implicit val ctx: Context = initCtx.fresh.withSettings(summary.sstate)
    val fileNames = CompilerCommand.checkUsage(summary)
    try {
      doCompile(newCompiler(), fileNames)
    } catch {
      case ex: Throwable =>
        ex match {
          case ex: FatalError  =>
            ctx.error(ex.getMessage) // signals that we should fail compilation.
            ctx.typerState.reporter
          case _ =>
            throw ex // unexpected error, tell the outside world.
        }
    }
  }

  def main(args: Array[String]): Unit =
    sys.exit(if (process(args).hasErrors) 1 else 0)
}

class FatalError(msg: String) extends Exception

