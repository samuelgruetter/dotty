package dotty.tools.dotc.transform

import dotty.tools.dotc._
import core._
import Contexts._
import Symbols._
import Decorators._
import TreeTransforms._
import dotty.tools.dotc.ast.tpd._


class RefChecks extends TreeTransform {

  override def name: String = "RefChecks"

  override def transformTypeDef(td: TypeDef)(implicit ctx: Context, info: TransformerInfo): Tree = {
    // println(i"-----> $td")
    for (newMember <- td.symbol.decls; oldMember <- newMember.allOverriddenSymbols) {
      println(s"${newMember.showFullName} overrides ${oldMember.showFullName}")
    }
    val s = td.symbol
    //println(i"${s.decls} /// ${s.decls.size}")
    td
  }  
    
  /*override def transformTemplate(temp: Template)(implicit ctx: Context, info: TransformerInfo): Tree = {
    val x = temp.self
    val s: Symbol = temp.symbol //tpe.classSymbol
    println(i"${s.decls} /// ${s.decls.size}")
    println(i">>> ${temp.denot} (${temp.denot.getClass}) has decls ${temp.tpe.classSymbol.decls.map(_.name).mkString}")
    temp
  }*/
  
  /*
   * /** mods class name template     or
   *  mods trait name template     or
   *  mods type name = rhs   or
   *  mods type name >: lo <: hi, if rhs = TypeBoundsTree(lo, hi) & (lo ne hi)
   */
  case class TypeDef
   */
    
  /*override def transformIdent(tree: Ident)(implicit ctx: Context, info: TransformerInfo): Tree = {
    val s: Symbol = tree.tpe.classSymbol
    println(i"${s.decls} /// ${s.decls.size}")
    println(i">>> ${tree.denot} (${tree.denot.getClass}) has decls ${tree.tpe.classSymbol.decls.map(_.name).mkString}")
    tree
  }*/
}
