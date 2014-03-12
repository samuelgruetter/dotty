package dotty.tools
package dotc

import core._
import Contexts._
import Periods._
import Symbols._
import typer.{FrontEnd, Typer, Mode, ImportInfo}
import reporting.ConsoleReporter
import dotty.tools.dotc.core.Phases.Phase

class Compiler {

  private def _phases: List[Phase] = List(new FrontEnd)

  def phases: List[Phase] = _phases
  
  /** Returns all phases until given phase, including given phase. Does not depend on `def phases`.*/
  def phasesUpTo(phase: String): List[Phase] = {
    val i = _phases.indexWhere(_.name == phase) // -1 if not found
    _phases.take(i+1)
  }
  
  var runId = 1
  def nextRunId = { runId += 1; runId }

  def rootContext(implicit ctx: Context): Context = {
    ctx.definitions.init(ctx)
    ctx.usePhases(phases)
    val start = ctx.fresh
      .withPeriod(Period(nextRunId, FirstPhaseId))
      .withOwner(defn.RootClass)
      .withTyper(new Typer)
      .withNewMode(Mode.ImplicitsEnabled)
      .withTyperState(new MutableTyperState(ctx.typerState, new ConsoleReporter()(ctx), isCommittable = true))
    ctx.definitions.init(start)
    def addImport(ctx: Context, sym: Symbol) =
      ctx.fresh.withImportInfo(ImportInfo.rootImport(sym)(ctx))
    (start.withRunInfo(new RunInfo(start)) /: defn.RootImports)(addImport)
  }

  def newRun(implicit ctx: Context): Run = {
    try new Run(this)(rootContext)
    finally {
      ctx.base.reset()
      ctx.runInfo.clear()
    }
  }
}