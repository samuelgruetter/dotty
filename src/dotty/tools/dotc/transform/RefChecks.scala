package dotty.tools.dotc.transform

import dotty.tools.dotc._
import core._
import Contexts._
import Symbols._
import TreeTransforms._
import dotty.tools.dotc.ast.tpd._


class RefChecks extends TreeTransform {

  override def name: String = "RefChecks"

  override def transformTemplate(tree: Template)(implicit ctx: Context, info: TransformerInfo): Tree = {
    println(">>>" + tree.symbol.asClass.name)
    tree
  }
}
