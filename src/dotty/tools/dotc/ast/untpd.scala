package dotty.tools
package dotc
package ast

import core._
import util.Positions._, Types._, Contexts._, Constants._, Names._, NameOps._, Flags._
import Denotations._, SymDenotations._, Symbols._, StdNames._, Annotations._, Trees._
import Decorators._
import language.higherKinds
import collection.mutable.ListBuffer

object untpd extends Trees.Instance[Untyped] with TreeInfo[Untyped] {

// ----- Tree cases that exist in untyped form only ------------------

  trait OpTree extends Tree {
    def op: Name
    override def isTerm = op.isTermName
    override def isType = op.isTypeName
  }

  /** A typed subtree of an untyped tree needs to be wrapped in a TypedSlice */
  case class TypedSplice(tree: tpd.Tree) extends ProxyTree {
    def forwardTo = tree
  }

  /** mods object name impl */
  case class ModuleDef(mods: Modifiers, name: TermName, impl: Template)
    extends MemberDef {
    type ThisTree[-T >: Untyped] <: Trees.NameTree[T] with Trees.MemberDef[T] with ModuleDef
    def withName(name: Name)(implicit ctx: Context) = cpy.ModuleDef(this, mods, name.toTermName, impl)
  }

  case class SymbolLit(str: String) extends TermTree
  case class InterpolatedString(id: TermName, strings: List[Literal], elems: List[Tree]) extends TermTree
  case class Function(args: List[Tree], body: Tree) extends Tree {
    override def isTerm = body.isTerm
    override def isType = body.isType
  }
  case class InfixOp(left: Tree, op: Name, right: Tree) extends OpTree
  case class PostfixOp(od: Tree, op: Name) extends OpTree
  case class PrefixOp(op: Name, od: Tree) extends OpTree
  case class Parens(t: Tree) extends ProxyTree {
    def forwardTo = t
  }
  case class Tuple(trees: List[Tree]) extends Tree {
    override def isTerm = trees.isEmpty || trees.head.isTerm
    override def isType = !isTerm
  }
  case class WhileDo(cond: Tree, body: Tree) extends TermTree
  case class DoWhile(body: Tree, cond: Tree) extends TermTree
  case class ForYield(enums: List[Tree], expr: Tree) extends TermTree
  case class ForDo(enums: List[Tree], body: Tree) extends TermTree
  case class GenFrom(pat: Tree, expr: Tree) extends Tree
  case class GenAlias(pat: Tree, expr: Tree) extends Tree
  case class ContextBounds(bounds: TypeBoundsTree, cxBounds: List[Tree]) extends TypTree
  case class PatDef(mods: Modifiers, pats: List[Tree], tpt: Tree, rhs: Tree) extends DefTree

  class PolyTypeDef(mods: Modifiers, name: TypeName, override val tparams: List[TypeDef], rhs: Tree)
    extends TypeDef(mods, name, rhs) {
    override def withName(name: Name)(implicit ctx: Context) = cpy.PolyTypeDef(this, mods, name.toTypeName, tparams, rhs)
  }

  // ------ Creation methods for untyped only -----------------

  def Ident(name: Name): Ident = new Ident(name)
  def BackquotedIdent(name: Name): BackquotedIdent = new BackquotedIdent(name)
  def Select(qualifier: Tree, name: Name): Select = new Select(qualifier, name)
  def SelectWithSig(qualifier: Tree, name: Name, sig: Signature): Select = new SelectWithSig(qualifier, name, sig)
  def This(qual: TypeName): This = new This(qual)
  def Super(qual: Tree, mix: TypeName): Super = new Super(qual, mix)
  def Apply(fun: Tree, args: List[Tree]): Apply = new Apply(fun, args)
  def TypeApply(fun: Tree, args: List[Tree]): TypeApply = new TypeApply(fun, args)
  def Literal(const: Constant): Literal = new Literal(const)
  def New(tpt: Tree): New = new New(tpt)
  def Pair(left: Tree, right: Tree): Pair = new Pair(left, right)
  def Typed(expr: Tree, tpt: Tree): Typed = new Typed(expr, tpt)
  def NamedArg(name: Name, arg: Tree): NamedArg = new NamedArg(name, arg)
  def Assign(lhs: Tree, rhs: Tree): Assign = new Assign(lhs, rhs)
  def Block(stats: List[Tree], expr: Tree): Block = new Block(stats, expr)
  def If(cond: Tree, thenp: Tree, elsep: Tree): If = new If(cond, thenp, elsep)
  def Closure(env: List[Tree], meth: Tree, tpt: Tree): Closure = new Closure(env, meth, tpt)
  def Match(selector: Tree, cases: List[CaseDef]): Match = new Match(selector, cases)
  def CaseDef(pat: Tree, guard: Tree, body: Tree): CaseDef = new CaseDef(pat, guard, body)
  def Return(expr: Tree, from: Tree): Return = new Return(expr, from)
  def Try(expr: Tree, handler: Tree, finalizer: Tree): Try = new Try(expr, handler, finalizer)
  def Throw(expr: Tree): Throw = new Throw(expr)
  def SeqLiteral(elems: List[Tree]): SeqLiteral = new SeqLiteral(elems)
  def JavaSeqLiteral(elems: List[Tree]): JavaSeqLiteral = new JavaSeqLiteral(elems)
  def TypeTree(original: Tree): TypeTree = new TypeTree(original)
  def TypeTree() = new TypeTree(EmptyTree)
  def SingletonTypeTree(ref: Tree): SingletonTypeTree = new SingletonTypeTree(ref)
  def SelectFromTypeTree(qualifier: Tree, name: Name): SelectFromTypeTree = new SelectFromTypeTree(qualifier, name)
  def AndTypeTree(left: Tree, right: Tree): AndTypeTree = new AndTypeTree(left, right)
  def OrTypeTree(left: Tree, right: Tree): OrTypeTree = new OrTypeTree(left, right)
  def RefinedTypeTree(tpt: Tree, refinements: List[Tree]): RefinedTypeTree = new RefinedTypeTree(tpt, refinements)
  def AppliedTypeTree(tpt: Tree, args: List[Tree]): AppliedTypeTree = new AppliedTypeTree(tpt, args)
  def ByNameTypeTree(result: Tree): ByNameTypeTree = new ByNameTypeTree(result)
  def TypeBoundsTree(lo: Tree, hi: Tree): TypeBoundsTree = new TypeBoundsTree(lo, hi)
  def Bind(name: Name, body: Tree): Bind = new Bind(name, body)
  def Alternative(trees: List[Tree]): Alternative = new Alternative(trees)
  def UnApply(fun: Tree, implicits: List[Tree], patterns: List[Tree]): UnApply = new UnApply(fun, implicits, patterns)
  def ValDef(mods: Modifiers, name: TermName, tpt: Tree, rhs: Tree): ValDef = new ValDef(mods, name, tpt, rhs)
  def DefDef(mods: Modifiers, name: TermName, tparams: List[TypeDef], vparamss: List[List[ValDef]], tpt: Tree, rhs: Tree): DefDef = new DefDef(mods, name, tparams, vparamss, tpt, rhs)
  def TypeDef(mods: Modifiers, name: TypeName, rhs: Tree): TypeDef = new TypeDef(mods, name, rhs)
  def Template(constr: DefDef, parents: List[Tree], self: ValDef, body: List[Tree]): Template = new Template(constr, parents, self, body)
  def Import(expr: Tree, selectors: List[untpd.Tree]): Import = new Import(expr, selectors)
  def PackageDef(pid: RefTree, stats: List[Tree]): PackageDef = new PackageDef(pid, stats)
  def Annotated(annot: Tree, arg: Tree): Annotated = new Annotated(annot, arg)
  def SharedTree(shared: Tree): SharedTree = new SharedTree(shared)

  // ------ Additional creation methods for untyped only -----------------

  // def TypeTree(tpe: Type): TypeTree = TypeTree().withType(tpe) todo: move to untpd/tpd

  /**     new pre.C[Ts](args1)...(args_n)
   *  ==>
   *      (new pre.C).<init>[Ts](args1)...(args_n)
   */
  def New(tpt: Tree, argss: List[List[Tree]])(implicit ctx: Context): Tree = {
    val (tycon, targs) = tpt match {
      case AppliedTypeTree(tycon, targs) =>
        (tycon, targs)
      case TypedSplice(AppliedTypeTree(tycon, targs)) =>
        (TypedSplice(tycon), targs map TypedSplice)
      case TypedSplice(tpt1: Tree) =>
        val argTypes = tpt1.tpe.argTypes
        val tycon = tpt1.tpe.withoutArgs(argTypes)
        def wrap(tpe: Type) = TypeTree(tpe) withPos tpt.pos
        (wrap(tycon), argTypes map wrap)
      case _ =>
        (tpt, Nil)
    }
    var prefix: Tree = Select(New(tycon), nme.CONSTRUCTOR)
    if (targs.nonEmpty) prefix = TypeApply(prefix, targs)
    ensureApplied((prefix /: argss)(Apply(_, _)))
  }

  def Block(stat: Tree, expr: Tree): Block =
    Block(stat :: Nil, expr)

  def Apply(fn: Tree, arg: Tree): Apply =
    Apply(fn, arg :: Nil)

  def ensureApplied(tpt: Tree) = tpt match {
    case _: Apply => tpt
    case _ => Apply(tpt, Nil)
  }

  def AppliedTypeTree(tpt: Tree, arg: Tree): AppliedTypeTree =
    AppliedTypeTree(tpt, arg :: Nil)

  def TypeTree(tpe: Type): TypedSplice = TypedSplice(TypeTree().withTypeUnchecked(tpe))

  def TypeDef(mods: Modifiers, name: TypeName, tparams: List[TypeDef], rhs: Tree): TypeDef =
    if (tparams.isEmpty) TypeDef(mods, name, rhs) else new PolyTypeDef(mods, name, tparams, rhs)

  def unitLiteral = Literal(Constant(()))

  def ref(tp: NamedType)(implicit ctx: Context): Tree =
    TypedSplice(tpd.ref(tp))

  def scalaUnit(implicit ctx: Context) = ref(defn.UnitClass.typeRef)

  def makeConstructor(mods: Modifiers, tparams: List[TypeDef], vparamss: List[List[ValDef]], rhs: Tree = EmptyTree)(implicit ctx: Context): DefDef =
    DefDef(mods, nme.CONSTRUCTOR, tparams, vparamss, TypeTree(), rhs)

  def emptyConstructor(implicit ctx: Context): DefDef =
    makeConstructor(Modifiers(), Nil, Nil)

  def makeSelfDef(name: TermName, tpt: Tree)(implicit ctx: Context) =
    ValDef(Modifiers(Private), name, tpt, EmptyTree)

  def makeTupleOrParens(ts: List[Tree])(implicit ctx: Context) = ts match {
    case t :: Nil => Parens(t)
    case _ => Tuple(ts)
  }

  def makeTuple(ts: List[Tree])(implicit ctx: Context) = ts match {
    case t :: Nil => t
    case _ => Tuple(ts)
  }

  def makeParameter(pname: TermName, tpe: Tree, mods: Modifiers = Modifiers())(implicit ctx: Context): ValDef =
    ValDef(mods | Param, pname, tpe, EmptyTree)

  def makeSyntheticParameter(n: Int = 1, tpt: Tree = TypeTree())(implicit ctx: Context): ValDef =
    ValDef(Modifiers(SyntheticTermParam), nme.syntheticParamName(n), tpt, EmptyTree)

  def refOfDef(tree: NameTree)(implicit ctx: Context) = Ident(tree.name)

// ------- A decorator for producing a path to a location --------------

  implicit class UntypedTreeDecorator(val self: Tree) extends AnyVal {
    def locateEnclosing(base: List[Tree], pos: Position): List[Tree] = {
      def encloses(elem: Any) = elem match {
        case t: Tree => t.envelope contains pos
        case _ => false
      }
      base.productIterator find encloses match {
        case Some(tree: Tree) => locateEnclosing(tree :: base, pos)
        case none => base
      }
    }
  }

// --------- Copier/Transformer/Accumulator classes for untyped trees -----

  override val cpy: UntypedTreeCopier = new UntypedTreeCopier

  class UntypedTreeCopier extends TreeCopier {
    def postProcess(tree: Tree, copied: Tree): copied.ThisTree[Untyped] =
      copied.asInstanceOf[copied.ThisTree[Untyped]]

    def ModuleDef(tree: Tree, mods: Modifiers, name: TermName, impl: Template) = tree match {
      case tree: ModuleDef if (mods eq tree.mods) && (name eq tree.name) && (impl eq tree.impl) => tree
      case _ => untpd.ModuleDef(mods, name, impl).withPos(tree.pos)
    }
    def PolyTypeDef(tree: Tree, mods: Modifiers, name: TypeName, tparams: List[TypeDef], rhs: Tree) = tree match {
      case tree: PolyTypeDef if (mods eq tree.mods) && (name eq tree.name) && (tparams eq tree.tparams) && (rhs eq tree.rhs) => tree
      case _ => new PolyTypeDef(mods, name, tparams, rhs).withPos(tree.pos)
    }
    def SymbolLit(tree: Tree, str: String) = tree match {
      case tree: SymbolLit if (str == tree.str) => tree
      case _ => untpd.SymbolLit(str).withPos(tree.pos)
    }
    def InterpolatedString(tree: Tree, id: TermName, strings: List[Literal], elems: List[Tree]) = tree match {
      case tree: InterpolatedString if (id eq tree.id) && (strings eq tree.strings) && (elems eq tree.elems) => tree
      case _ => untpd.InterpolatedString(id, strings, elems).withPos(tree.pos)
    }
    def Function(tree: Tree, args: List[Tree], body: Tree) = tree match {
      case tree: Function if (args eq tree.args) && (body eq tree.body) => tree
      case _ => untpd.Function(args, body).withPos(tree.pos)
    }
    def InfixOp(tree: Tree, left: Tree, op: Name, right: Tree) = tree match {
      case tree: InfixOp if (left eq tree.left) && (op eq tree.op) && (right eq tree.right) => tree
      case _ => untpd.InfixOp(left, op, right).withPos(tree.pos)
    }
    def PostfixOp(tree: Tree, od: Tree, op: Name) = tree match {
      case tree: PostfixOp if (od eq tree.od) && (op eq tree.op) => tree
      case _ => untpd.PostfixOp(od, op).withPos(tree.pos)
    }
    def PrefixOp(tree: Tree, op: Name, od: Tree) = tree match {
      case tree: PrefixOp if (op eq tree.op) && (od eq tree.od) => tree
      case _ => untpd.PrefixOp(op, od).withPos(tree.pos)
    }
    def Parens(tree: Tree, t: Tree) = tree match {
      case tree: Parens if (t eq tree.t) => tree
      case _ => untpd.Parens(t).withPos(tree.pos)
    }
    def Tuple(tree: Tree, trees: List[Tree]) = tree match {
      case tree: Tuple if (trees eq tree.trees) => tree
      case _ => untpd.Tuple(trees).withPos(tree.pos)
    }
    def WhileDo(tree: Tree, cond: Tree, body: Tree) = tree match {
      case tree: WhileDo if (cond eq tree.cond) && (body eq tree.body) => tree
      case _ => untpd.WhileDo(cond, body).withPos(tree.pos)
    }
    def DoWhile(tree: Tree, body: Tree, cond: Tree) = tree match {
      case tree: DoWhile if (body eq tree.body) && (cond eq tree.cond) => tree
      case _ => untpd.DoWhile(body, cond).withPos(tree.pos)
    }
    def ForYield(tree: Tree, enums: List[Tree], expr: Tree) = tree match {
      case tree: ForYield if (enums eq tree.enums) && (expr eq tree.expr) => tree
      case _ => untpd.ForYield(enums, expr).withPos(tree.pos)
    }
    def ForDo(tree: Tree, enums: List[Tree], body: Tree) = tree match {
      case tree: ForDo if (enums eq tree.enums) && (body eq tree.body) => tree
      case _ => untpd.ForDo(enums, body).withPos(tree.pos)
    }
    def GenFrom(tree: Tree, pat: Tree, expr: Tree) = tree match {
      case tree: GenFrom if (pat eq tree.pat) && (expr eq tree.expr) => tree
      case _ => untpd.GenFrom(pat, expr).withPos(tree.pos)
    }
    def GenAlias(tree: Tree, pat: Tree, expr: Tree) = tree match {
      case tree: GenAlias if (pat eq tree.pat) && (expr eq tree.expr) => tree
      case _ => untpd.GenAlias(pat, expr).withPos(tree.pos)
    }
    def ContextBounds(tree: Tree, bounds: TypeBoundsTree, cxBounds: List[Tree]) = tree match {
      case tree: ContextBounds if (bounds eq tree.bounds) && (cxBounds eq tree.cxBounds) => tree
      case _ => untpd.ContextBounds(bounds, cxBounds).withPos(tree.pos)
    }
    def PatDef(tree: Tree, mods: Modifiers, pats: List[Tree], tpt: Tree, rhs: Tree) = tree match {
      case tree: PatDef if (mods eq tree.mods) && (pats eq tree.pats) && (tpt eq tree.tpt) && (rhs eq tree.rhs) => tree
      case _ => untpd.PatDef(mods, pats, tpt, rhs).withPos(tree.pos)
    }
  }

  abstract class UntypedTreeTransformer(cpy: UntypedTreeCopier = untpd.cpy) extends TreeTransformer(cpy) {
    override def transform(tree: Tree)(implicit ctx: Context): Tree = tree match {
      case ModuleDef(mods, name, impl) =>
        cpy.ModuleDef(tree, mods, name, transformSub(impl))
      case SymbolLit(str) =>
        cpy.SymbolLit(tree, str)
      case InterpolatedString(id, strings, elems) =>
        cpy.InterpolatedString(tree, id, transformSub(strings), transform(elems))
      case Function(args, body) =>
        cpy.Function(tree, transform(args), transform(body))
      case InfixOp(left, op, right) =>
        cpy.InfixOp(tree, transform(left), op, transform(right))
      case PostfixOp(od, op) =>
        cpy.PostfixOp(tree, transform(od), op)
      case PrefixOp(op, od) =>
        cpy.PrefixOp(tree, op, transform(od))
      case Parens(t) =>
        cpy.Parens(tree, transform(t))
      case Tuple(trees) =>
        cpy.Tuple(tree, transform(trees))
      case WhileDo(cond, body) =>
        cpy.WhileDo(tree, transform(cond), transform(body))
      case DoWhile(body, cond) =>
        cpy.DoWhile(tree, transform(body), transform(cond))
      case ForYield(enums, expr) =>
        cpy.ForYield(tree, transform(enums), transform(expr))
      case ForDo(enums, body) =>
        cpy.ForDo(tree, transform(enums), transform(body))
      case GenFrom(pat, expr) =>
        cpy.GenFrom(tree, transform(pat), transform(expr))
      case GenAlias(pat, expr) =>
        cpy.GenAlias(tree, transform(pat), transform(expr))
      case ContextBounds(bounds, cxBounds) =>
        cpy.ContextBounds(tree, transformSub(bounds), transform(cxBounds))
      case PatDef(mods, pats, tpt, rhs) =>
        cpy.PatDef(tree, mods, transform(pats), transform(tpt), transform(rhs))
      case tree: PolyTypeDef =>
        cpy.PolyTypeDef(tree, tree.mods, tree.name, transformSub(tree.tparams), transform(tree.rhs))
      case _ =>
        super.transform(tree)
    }
  }

  abstract class UntypedTreeAccumulator[X] extends TreeAccumulator[X] {
    override def foldOver(x: X, tree: Tree): X = tree match {
      case ModuleDef(mods, name, impl) =>
        this(x, impl)
      case SymbolLit(str) =>
        x
      case InterpolatedString(id, strings, elems) =>
        this(this(x, strings), elems)
      case Function(args, body) =>
        this(this(x, args), body)
      case InfixOp(left, op, right) =>
        this(this(x, left), right)
      case PostfixOp(od, op) =>
        this(x, od)
      case PrefixOp(op, od) =>
        this(x, od)
      case Parens(t) =>
        this(x, t)
      case Tuple(trees) =>
        this(x, trees)
      case WhileDo(cond, body) =>
        this(this(x, cond), body)
      case DoWhile(body, cond) =>
        this(this(x, body), cond)
      case ForYield(enums, expr) =>
        this(this(x, enums), expr)
      case ForDo(enums, body) =>
        this(this(x, enums), body)
      case GenFrom(pat, expr) =>
        this(this(x, pat), expr)
      case GenAlias(pat, expr) =>
        this(this(x, pat), expr)
      case ContextBounds(bounds, cxBounds) =>
        this(this(x, bounds), cxBounds)
      case PatDef(mods, pats, tpt, rhs) =>
        this(this(this(x, pats), tpt), rhs)
      case tree: PolyTypeDef =>
        this(this(x, tree.tparams), tree.rhs)
      case _ =>
        super.foldOver(x, tree)
    }
  }
}
