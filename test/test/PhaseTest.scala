package test

import dotty.tools.dotc.core._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.core.Flags._
import Types._, Symbols._, Decorators._
import dotty.tools.dotc.printing.Texts._
import dotty.tools.dotc.reporting._
import dotty.tools.dotc.core.Decorators._
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.Compiler
import dotty.tools.dotc.reporting.Reporter._
import dotty.tools.dotc.core.Phases.Phase

import org.junit.Assert._

class PhaseTest(val checkAfterPhase: String) extends DottyTest {

  private def compilerWithChecker(phase: String)(assertion: (tpd.Tree, Context) => Unit) = new Compiler {
    override def phases = {
      val checker = new Phase{
        def name = "assertionChecker"
        override def run(implicit ctx: Context): Unit = {
          assertEquals("Expected no errors.", 0, ctx.reporter.count(ERROR.level))
          assertion(ctx.compilationUnit.tpdTree, ctx)
        }
      }
      phasesUpTo(phase) ::: List(checker)
    }
  }

  def checkAccepted(source: String)(assertion: (tpd.Tree, Context) => Unit): Unit = {
    val c = compilerWithChecker(checkAfterPhase)(assertion)
    val run = c.newRun
    run.compile(source)
  }

  def checkCompiles(source: String): Unit = checkAccepted(source)((tree, context) => ())
  
  def checkRejected(source: String)(errorChecks: (Diagnostic => Unit)*): Unit = {
    val initCtx = (new ContextBase).initialCtx
    val c = new Compiler {
      override def phases = phasesUpTo(checkAfterPhase)
    }
    val rep = new StoreReporter
    val run = c.newRun(rep)(initCtx)
    run.compile(source)
    assertEquals("Number of errors does not match.", errorChecks.size, rep.diagnostics.size)
    for ((checkFunc, diag) <- errorChecks zip rep.diagnostics) checkFunc(diag)
  }

  def checkDoesntCompile(source: String, nErrors: Int) = checkRejected(source)(List.fill(nErrors)((d: Diagnostic) => ()): _*)
}
