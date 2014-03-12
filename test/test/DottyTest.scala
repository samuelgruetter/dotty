package test

import dotty.tools.dotc.core.Contexts._

class DottyTest {

  dotty.tools.dotc.parsing.Scanners // initialize keywords

  implicit def ctx: Context = {
    val base = new ContextBase
    import base.settings._
    val ctx = base.initialCtx.fresh
    //  .withSetting(verbose, true)
    //  .withSetting(Ylogcp, true)
      .withSetting(printtypes, true)
      .withSetting(pageWidth, 90)
      .withSetting(log, List("<some"))
    base.definitions.init(ctx)
    ctx
  }

}
