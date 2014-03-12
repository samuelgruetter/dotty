package test

import dotty.tools.dotc.core._
import dotty.tools.dotc.core.Contexts._
import dotty.tools.dotc.core.Symbols._
import dotty.tools.dotc.core.Flags._
import dotty.tools.dotc.printing.Texts._
import dotty.tools.dotc.reporting.ConsoleReporter
import dotty.tools.dotc.core.Decorators._
import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.Compiler
import dotty.tools.dotc
import dotty.tools.dotc.core.Phases.Phase

import dotty.tools.dotc.reporting.Reporter._
import dotty.tools.dotc.reporting.StoreReporter

import org.junit.Test
import org.junit.Assert._

class PhaseTest {

  private def initCtx = (new ContextBase).initialCtx

  
  def checkRejected(checkAfterPhase: String = "frontend", source: String)(errorCheck: Diagnostic => Unit): Unit = {
    val c = new Compiler {
      override def phases = phasesUpTo(checkAfterPhase)
    }
    implicit val ctx: Context = initCtx
    c.rootContext(ctx)
    val rep = new StoreReporter
    val run = c.newRunWithReporter(rep)
    run.compile(source)
    
    /*
    implicit val ctx: Context = initCtx
    c.rootContextWithReporter(Some(rep))(ctx)
    val run = c.newRun
    run.compile(source)
    */
    println(">>>>>>" + rep.diagnostics)
    // assertTrue("expected an exception to occur, but there was none", thrown)
    
  }
  
  @Test def testBoundsInNewAreChecked = checkRejected("frontend", """
    object test808542 {
      def cast[T, U](t: T): U = {
        val c = new {
          type S >: T <: U
        }
        (t: c.S)
      }
    }"""
  )(err => assertEquals(err.getMessage, "lower bound T does not conform to upper bound U"))

}
