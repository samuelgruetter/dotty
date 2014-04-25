package dotty.tools
package dotc
package ast

import core._
import Flags._, Trees._, Types._, Contexts._
import Names._, StdNames._, NameOps._, Decorators._, Symbols._
import util.HashSet

trait TreeInfo[T >: Untyped <: Type] { self: Trees.Instance[T] =>

  // Note: the <: Type constraint looks necessary (and is needed to make the file compile in dotc).
  // But Scalac accepts the program happily without it. Need to find out why.

  def unsplice[T >: Untyped](tree: Trees.Tree[T]): Trees.Tree[T] = tree.asInstanceOf[untpd.Tree] match {
    case untpd.TypedSplice(tree1) => tree1.asInstanceOf[Trees.Tree[T]]
    case _ => tree
  }

  def isDeclarationOrTypeDef(tree: Tree): Boolean = unsplice(tree) match {
    case DefDef(_, _, _, _, _, EmptyTree)
      | ValDef(_, _, _, EmptyTree)
      | TypeDef(_, _, _) => true
    case _ => false
  }

  /** Is tree legal as a member definition of an interface?
   */
  def isInterfaceMember(tree: Tree): Boolean = unsplice(tree) match {
    case EmptyTree => true
    case Import(_, _) => true
    case TypeDef(_, _, _) => true
    case DefDef(mods, _, _, _, _, __) => mods.flags is Deferred
    case ValDef(mods, _, _, _) => mods is Deferred
    case _ => false
  }

  def isOpAssign(tree: Tree) = unsplice(tree) match {
    case Apply(fn, _ :: Nil) =>
      unsplice(fn) match {
        case Select(_, name) if name.isOpAssignmentName => true
        case _ => false
      }
    case _ => false
  }

  class MatchingArgs(params: List[Symbol], args: List[Tree])(implicit ctx: Context) {
    def foreach(f: (Symbol, Tree) => Unit): Boolean = {
      def recur(params: List[Symbol], args: List[Tree]): Boolean = params match {
        case Nil => args.isEmpty
        case param :: params1 =>
          if (param.info.isRepeatedParam) {
            for (arg <- args) f(param, arg)
            true
          } else args match {
            case Nil => false
            case arg :: args1 =>
              f(param, args.head)
              recur(params1, args1)
          }
      }
      recur(params, args)
    }
    def zipped: List[(Symbol, Tree)] = map((_, _))
    def map[R](f: (Symbol, Tree) => R): List[R] = {
      val b = List.newBuilder[R]
      foreach(b += f(_, _))
      b.result
    }
  }

  /** The method part of an application node, possibly enclosed in a block
   *  with only valdefs as statements. the reason for also considering blocks
   *  is that named arguments can transform a call into a block, e.g.
   *   <init>(b = foo, a = bar)
   * is transformed to
   *   { val x$1 = foo
   *     val x$2 = bar
   *     <init>(x$2, x$1)
   *   }
   */
  def methPart(tree: Tree): Tree = stripApply(tree) match {
    case TypeApply(fn, _) => methPart(fn)
    case AppliedTypeTree(fn, _) => methPart(fn) // !!! should not be needed
    case Block(stats, expr) => methPart(expr)
    case mp => mp
  }

  /** If tree is a closure, it's body, otherwise tree itself */
  def closureBody(tree: tpd.Tree): tpd.Tree = tree match {
    case Block(DefDef(_, nme.ANON_FUN, _, _, _, rhs) :: Nil, Closure(_, _, _)) => rhs
    case _ => tree
  }

  /** If this is an application, its function part, stripping all
   *  Apply nodes (but leaving TypeApply nodes in). Otherwise the tree itself.
   */
  def stripApply(tree: Tree): Tree = unsplice(tree) match {
    case Apply(fn, _) => stripApply(fn)
    case _ => tree
  }

  /** The number of arguments in an application */
  def numArgs(tree: Tree): Int = unsplice(tree) match {
    case Apply(fn, args) => numArgs(fn) + args.length
    case TypeApply(fn, args) => numArgs(fn)
    case Block(stats, expr) => numArgs(expr)
    case _ => 0
  }

  /** Is tree a self constructor call this(...)? I.e. a call to a constructor of the
   *  same object?
   */
  def isSelfConstrCall(tree: Tree): Boolean = methPart(tree) match {
    case Ident(nme.CONSTRUCTOR) | Select(This(_), nme.CONSTRUCTOR) => true
    case _ => false
  }

  /** Is tree a super constructor call?
   */
  def isSuperConstrCall(tree: Tree): Boolean = methPart(tree) match {
    case Select(Super(_, _), nme.CONSTRUCTOR) => true
    case _ => false
  }

  def isSuperSelection(tree: untpd.Tree) = unsplice(tree) match {
    case Select(Super(_, _), _) => true
    case _ => false
  }

  def isSelfOrSuperConstrCall(tree: Tree): Boolean = methPart(tree) match {
    case Ident(nme.CONSTRUCTOR)
       | Select(This(_), nme.CONSTRUCTOR)
       | Select(Super(_, _), nme.CONSTRUCTOR) => true
    case _ => false
  }

  /** Is tree a variable pattern? */
  def isVarPattern(pat: untpd.Tree): Boolean = unsplice(pat) match {
    case x: BackquotedIdent => false
    case x: Ident => x.name.isVariableName
    case _  => false
  }

  /** The first constructor definition in `stats` */
  def firstConstructor(stats: List[Tree]): Tree = stats match {
    case (meth: DefDef) :: _ if meth.name.isConstructorName => meth
    case stat :: stats => firstConstructor(stats)
    case nil => EmptyTree
  }

  /** The arguments to the first constructor in `stats`. */
  def firstConstructorArgs(stats: List[Tree]): List[Tree] = firstConstructor(stats) match {
    case DefDef(_, _, _, args :: _, _, _) => args
    case _                                => Nil
  }

  /** The value definitions marked PRESUPER in this statement sequence */
  def preSuperFields(stats: List[Tree]): List[ValDef] =
    (stats filter isEarlyValDef).asInstanceOf[List[ValDef]]

  def isEarlyDef(tree: Tree) = isEarlyValDef(tree) || isEarlyTypeDef(tree)

  def isEarlyValDef(tree: Tree) = unsplice(tree) match {
    case ValDef(mods, _, _, _) => mods is Scala2PreSuper
    case _ => false
  }

  def isEarlyTypeDef(tree: Tree) = unsplice(tree) match {
    case TypeDef(mods, _, _) => mods is Scala2PreSuper
    case _ => false
  }

  /** Is tpt a vararg type of the form T* ? */
  def isRepeatedParamType(tpt: Tree)(implicit ctx: Context) = tpt match {
    case tpt: TypeTree => tpt.typeOpt.isRepeatedParam
    case AppliedTypeTree(Select(_, tpnme.REPEATED_PARAM_CLASS), _)      => true
    case AppliedTypeTree(Select(_, tpnme.JAVA_REPEATED_PARAM_CLASS), _) => true
    case _                                                              => false
  }

  /** Is name a left-associative operator? */
  def isLeftAssoc(operator: Name) = operator.nonEmpty && (operator.last != ':')

  /** can this type be a type pattern? */
  def mayBeTypePat(tree: untpd.Tree): Boolean = unsplice(tree) match {
    case AndTypeTree(tpt1, tpt2) => mayBeTypePat(tpt1) || mayBeTypePat(tpt2)
    case OrTypeTree(tpt1, tpt2) => mayBeTypePat(tpt1) || mayBeTypePat(tpt2)
    case RefinedTypeTree(tpt, refinements) => mayBeTypePat(tpt) || refinements.exists(_.isInstanceOf[Bind])
    case AppliedTypeTree(tpt, args) => mayBeTypePat(tpt) || args.exists(_.isInstanceOf[Bind])
    case SelectFromTypeTree(tpt, _) => mayBeTypePat(tpt)
    case Annotated(_, tpt) => mayBeTypePat(tpt)
    case _ => false
  }

  /** Is this argument node of the form <expr> : _* ?
   */
  def isWildcardStarArg(tree: untpd.Tree)(implicit ctx: Context): Boolean = unsplice(tree) match {
    case Typed(_, Ident(tpnme.WILDCARD_STAR)) => true
    case Typed(_, tpt: TypeTree) => tpt.hasType && tpt.tpe.isRepeatedParam
    case _ => false
  }

  /** If this tree has type parameters, those.  Otherwise Nil.
  def typeParameters(tree: Tree): List[TypeDef] = tree match {
    case DefDef(_, _, tparams, _, _, _) => tparams
    case ClassDef(_, _, tparams, _)     => tparams
    case TypeDef(_, _, tparams, _)      => tparams
    case _                              => Nil
  }*/

  /** Does this argument list end with an argument of the form <expr> : _* ? */
  def isWildcardStarArgList(trees: List[Tree])(implicit ctx: Context) =
    trees.nonEmpty && isWildcardStarArg(trees.last)

  /** Is the argument a wildcard argument of the form `_` or `x @ _`?
   */
  def isWildcardArg(tree: Tree): Boolean = unbind(tree) match {
    case Ident(nme.WILDCARD) => true
    case _                   => false
  }

  /** Is this pattern node a catch-all (wildcard or variable) pattern? */
  def isDefaultCase(cdef: CaseDef) = cdef match {
    case CaseDef(pat, EmptyTree, _) => isWildcardArg(pat)
    case _                            => false
  }

  /** Is this pattern node a synthetic catch-all case, added during PartialFuction synthesis before we know
    * whether the user provided cases are exhaustive. */
  def isSyntheticDefaultCase(cdef: CaseDef) = unsplice(cdef) match {
    case CaseDef(Bind(nme.DEFAULT_CASE, _), EmptyTree, _) => true
    case _                                                  => false
  }

  /** Does this CaseDef catch Throwable? */
  def catchesThrowable(cdef: CaseDef)(implicit ctx: Context) =
    catchesAllOf(cdef, defn.ThrowableClass.typeRef)

  /** Does this CaseDef catch everything of a certain Type? */
  def catchesAllOf(cdef: CaseDef, threshold: Type)(implicit ctx: Context) =
    isDefaultCase(cdef) ||
    cdef.guard.isEmpty && {
      unbind(cdef.pat) match {
        case Typed(Ident(nme.WILDCARD), tpt) => threshold <:< tpt.typeOpt
        case _                               => false
      }
    }

  /** Is this case guarded? */
  def isGuardedCase(cdef: CaseDef) = cdef.guard ne EmptyTree

  /** True iff definition if a val or def with no right-hand-side, or it
   *  is an abstract typoe declaration
   */
  def lacksDefinition(mdef: MemberDef) = mdef match {
    case mdef: ValOrDefDef => mdef.rhs.isEmpty && !mdef.name.isConstructorName
    case mdef: TypeDef => mdef.rhs.isEmpty || mdef.rhs.isInstanceOf[TypeBoundsTree]
    case _ => false
  }

  /** The underlying pattern ignoring any bindings */
  def unbind(x: Tree): Tree = unsplice(x) match {
    case Bind(_, y) => unbind(y)
    case y          => y
  }

  /** Checks whether predicate `p` is true for all result parts of this epression,
   *  where we zoom into Ifs, Matches, and Blocks.
   */
  def forallResults(tree: Tree, p: Tree => Boolean): Boolean = tree match {
    case If(_, thenp, elsep) => forallResults(thenp, p) && forallResults(elsep, p)
    case Match(_, cases) => cases forall (c => forallResults(c.body, p))
    case Block(_, expr) => forallResults(expr, p)
    case _ => p(tree)
  }
}

trait UntypedTreeInfo extends TreeInfo[Untyped] { self: Trees.Instance[Untyped] =>
  // todo: fill with methods from TreeInfo that only apply to untpd.Tree's
}

trait TypedTreeInfo extends TreeInfo[Type] { self: Trees.Instance[Type] =>

  /** Is tree a definition that has no side effects when
   *  evaluated as part of a block after the first time?
   */
  def isIdempotentDef(tree: tpd.Tree)(implicit ctx: Context): Boolean = unsplice(tree) match {
    case EmptyTree
       | TypeDef(_, _, _)
       | Import(_, _)
       | DefDef(_, _, _, _, _, _) =>
      true
    case ValDef(mods, _, _, rhs) =>
      !(mods is Mutable) && isIdempotentExpr(rhs)
    case _ =>
      false
  }

  /** Is tree an expression which can be inlined without affecting program semantics?
   *
   *  Note that this is not called "isExprPure" since purity (lack of side-effects)
   *  is not the litmus test.  References to modules and lazy vals are side-effecting,
   *  both because side-effecting code may be executed and because the first reference
   *  takes a different code path than all to follow; but they are safe to inline
   *  because the expression result from evaluating them is always the same.
   */
  def isIdempotentExpr(tree: tpd.Tree)(implicit ctx: Context): Boolean = unsplice(tree) match {
    case EmptyTree
       | This(_)
       | Super(_, _)
       | Literal(_) =>
      true
    case Ident(_) =>
      isIdempotentRef(tree)
    case Select(qual, _) =>
      isIdempotentRef(tree) && isIdempotentExpr(qual)
    case TypeApply(fn, _) =>
      isIdempotentExpr(fn)
/*
 * Not sure we'll need that. Comment out until we find out
    case Apply(Select(free @ Ident(_), nme.apply), _) if free.symbol.name endsWith nme.REIFY_FREE_VALUE_SUFFIX =>
      // see a detailed explanation of this trick in `GenSymbols.reifyFreeTerm`
      free.symbol.hasStableFlag && isIdempotentExpr(free)
*/
    case Apply(fn, Nil) =>
      // Note: After uncurry, field accesses are represented as Apply(getter, Nil),
      // so an Apply can also be pure.
      // However, before typing, applications of nullary functional values are also
      // Apply(function, Nil) trees. To prevent them from being treated as pure,
      // we check that the callee is a method.
      // The callee might also be a Block, which has a null symbol, so we guard against that (SI-7185)
      fn.symbol != null && (fn.symbol is (Method, butNot = Lazy)) && isIdempotentExpr(fn)
    case Typed(expr, _) =>
      isIdempotentExpr(expr)
    case Block(stats, expr) =>
      (stats forall isIdempotentDef) && isIdempotentExpr(expr)
    case _ =>
      false
  }

  def isIdempotentRef(tree: tpd.Tree)(implicit ctx: Context) =
    tree.symbol.isStable || !tree.tpe.widen.isParameterless

  /** Is symbol potentially a getter of a mutable variable?
   */
  def mayBeVarGetter(sym: Symbol)(implicit ctx: Context): Boolean = {
    def maybeGetterType(tpe: Type): Boolean = tpe match {
      case _: ExprType | _: ImplicitMethodType => true
      case tpe: PolyType => maybeGetterType(tpe.resultType)
      case _ => false
    }
    sym.owner.isClass && !sym.isStable && maybeGetterType(sym.info)
  }

  /** Is tree a reference to a mutable variable, or to a potential getter
   *  that has a setter in the same class?
   */
  def isVariableOrGetter(tree: tpd.Tree)(implicit ctx: Context) = {
    def sym = tree.symbol
    def isVar    = sym is Mutable
    def isGetter =
      mayBeVarGetter(sym) && sym.owner.info.member(sym.name.asTermName.setterName).exists

    unsplice(tree) match {
      case Ident(_) => isVar
      case Select(_, _) => isVar || isGetter
      case Apply(_, _) =>
        methPart(tree) match {
          case Select(qual, nme.apply) => qual.tpe.member(nme.update).exists
          case _ => false
        }
      case _ => false
    }
  }

  /** Is tree a `this` node which belongs to `enclClass`? */
  def isSelf(tree: Tree, enclClass: Symbol)(implicit ctx: Context): Boolean = unsplice(tree) match {
    case This(_) => tree.symbol == enclClass
    case _ => false
  }

  /** Strips layers of `.asInstanceOf[T]` / `_.$asInstanceOf[T]()` from an expression */
  def stripCast(tree: tpd.Tree)(implicit ctx: Context): tpd.Tree = {
    def isCast(sel: tpd.Tree) = defn.asInstanceOfMethods contains sel.symbol
    unsplice(tree) match {
      case TypeApply(sel @ Select(inner, _), _) if isCast(sel) =>
        stripCast(inner)
      case Apply(TypeApply(sel @ Select(inner, _), _), Nil) if isCast(sel) =>
        stripCast(inner)
      case t =>
        t
    }
  }

  /** Is this pattern node a catch-all or type-test pattern? */
  def isCatchCase(cdef: CaseDef)(implicit ctx: Context) = cdef match {
    case CaseDef(Typed(Ident(nme.WILDCARD), tpt), EmptyTree, _) =>
      isSimpleThrowable(tpt.tpe)
    case CaseDef(Bind(_, Typed(Ident(nme.WILDCARD), tpt)), EmptyTree, _) =>
      isSimpleThrowable(tpt.tpe)
    case _ =>
      isDefaultCase(cdef)
  }

  private def isSimpleThrowable(tp: Type)(implicit ctx: Context): Boolean = tp match {
    case tp @ TypeRef(pre, _) =>
      (pre == NoPrefix || pre.widen.typeSymbol.isStatic) &&
      (tp.symbol derivesFrom defn.ThrowableClass) && !(tp.symbol is Trait)
    case _ =>
      false
  }

  /** If `tree` is a DefTree, the symbol defined by it, otherwise NoSymbol */
  def definedSym(tree: Tree)(implicit ctx: Context): Symbol =
    if (tree.isDef) tree.symbol else NoSymbol

  /** Going from child to parent, the path of tree nodes that starts
   *  with a definition of symbol `sym` and ends with `root`, or Nil
   *  if no such path exists.
   *  Pre: `sym` must have a position.
   */
  def defPath(sym: Symbol, root: Tree)(implicit ctx: Context): List[Tree] = ctx.debugTraceIndented(i"defpath($sym with position ${sym.pos}, ${root.show})") {
    require(sym.pos.exists)
    object accum extends TreeAccumulator[List[Tree]] {
      def apply(x: List[Tree], tree: Tree): List[Tree] = {
        if (tree.envelope.contains(sym.pos))
          if (definedSym(tree) == sym) tree :: x
          else {
            val x1 = foldOver(x, tree)
            if (x1 ne x) tree :: x1 else x1
          }
        else x
      }
    }
    accum(Nil, root)
  }

  /** The statement sequence that contains a definition of `sym`, or Nil
   *  if none was found.
   *  For a tree to be found, The symbol must have a position and its definition
   *  tree must be reachable from come tree stored in an enclosing context.
   */
  def definingStats(sym: Symbol)(implicit ctx: Context): List[Tree] =
    if (!sym.pos.exists || (ctx eq NoContext) || ctx.compilationUnit == null) Nil
    else defPath(sym, ctx.compilationUnit.tpdTree) match {
      case defn :: encl :: _ =>
        def verify(stats: List[Tree]) =
          if (stats exists (definedSym(_) == sym)) stats else Nil
        encl match {
          case Block(stats, _) => verify(stats)
          case Template(_, _, _, stats) => verify(stats)
          case PackageDef(_, stats) => verify(stats)
          case _ => Nil
        }
      case nil =>
        Nil
    }
}

  /** a Match(Typed(_, tpt), _) must be translated into a switch if isSwitchAnnotation(tpt.tpe)
  def isSwitchAnnotation(tpe: Type) = tpe hasAnnotation defn.SwitchClass
  */

  /** Does list of trees start with a definition of
   *  a class of module with given name (ignoring imports)
  def firstDefinesClassOrObject(trees: List[Tree], name: Name): Boolean = trees match {
      case Import(_, _) :: xs               => firstDefinesClassOrObject(xs, name)
      case Annotated(_, tree1) :: Nil       => firstDefinesClassOrObject(List(tree1), name)
      case ModuleDef(_, `name`, _) :: Nil   => true
      case ClassDef(_, `name`, _, _) :: Nil => true
      case _                                => false
    }


  /** Is this file the body of a compilation unit which should not
   *  have Predef imported?
   */
  def noPredefImportForUnit(body: Tree) = {
    // Top-level definition whose leading imports include Predef.
    def isLeadingPredefImport(defn: Tree): Boolean = defn match {
      case PackageDef(_, defs1) => defs1 exists isLeadingPredefImport
      case Import(expr, _)      => isReferenceToPredef(expr)
      case _                    => false
    }
    // Compilation unit is class or object 'name' in package 'scala'
    def isUnitInScala(tree: Tree, name: Name) = tree match {
      case PackageDef(Ident(nme.scala_), defs) => firstDefinesClassOrObject(defs, name)
      case _                                   => false
    }

    isUnitInScala(body, nme.Predef) || isLeadingPredefImport(body)
  }
   */

  /*
  def isAbsTypeDef(tree: Tree) = tree match {
    case TypeDef(_, _, _, TypeBoundsTree(_, _)) => true
    case TypeDef(_, _, _, rhs) => rhs.tpe.isInstanceOf[TypeBounds]
    case _ => false
  }

  def isAliasTypeDef(tree: Tree) = tree match {
    case TypeDef(_, _, _, _) => !isAbsTypeDef(tree)
    case _ => false
  }

  /** Some handy extractors for spotting trees through the
   *  the haze of irrelevant braces: i.e. Block(Nil, SomeTree)
   *  should not keep us from seeing SomeTree.
   */
  abstract class SeeThroughBlocks[T] {
    protected def unapplyImpl(x: Tree): T
    def unapply(x: Tree): T = x match {
      case Block(Nil, expr)         => unapply(expr)
      case _                        => unapplyImpl(x)
    }
  }
  object IsTrue extends SeeThroughBlocks[Boolean] {
    protected def unapplyImpl(x: Tree): Boolean = x match {
      case Literal(Constant(true)) => true
      case _                       => false
    }
  }
  object IsFalse extends SeeThroughBlocks[Boolean] {
    protected def unapplyImpl(x: Tree): Boolean = x match {
      case Literal(Constant(false)) => true
      case _                        => false
    }
  }
  object IsIf extends SeeThroughBlocks[Option[(Tree, Tree, Tree)]] {
    protected def unapplyImpl(x: Tree) = x match {
      case If(cond, thenp, elsep) => Some((cond, thenp, elsep))
      case _                      => None
    }
  }

  def isApplyDynamicName(name: Name) = (name == nme.updateDynamic) || (name == nme.selectDynamic) || (name == nme.applyDynamic) || (name == nme.applyDynamicNamed)

  class DynamicApplicationExtractor(nameTest: Name => Boolean) {
    def unapply(tree: Tree) = tree match {
      case Apply(TypeApply(Select(qual, oper), _), List(Literal(Constant(name)))) if nameTest(oper) => Some((qual, name))
      case Apply(Select(qual, oper), List(Literal(Constant(name)))) if nameTest(oper) => Some((qual, name))
      case Apply(Ident(oper), List(Literal(Constant(name)))) if nameTest(oper) => Some((EmptyTree(), name))
      case _ => None
    }
  }
  object DynamicUpdate extends DynamicApplicationExtractor(_ == nme.updateDynamic)
  object DynamicApplication extends DynamicApplicationExtractor(isApplyDynamicName)
  object DynamicApplicationNamed extends DynamicApplicationExtractor(_ == nme.applyDynamicNamed)

  object MacroImplReference {
    private def refPart(tree: Tree): Tree = tree match {
      case TypeApply(fun, _) => refPart(fun)
      case ref: RefTree => ref
      case _ => EmptyTree()
    }

    def unapply(tree: Tree) = refPart(tree) match {
      case ref: RefTree => Some((ref.qualifier.symbol, ref.symbol, dissectApplied(tree).targs))
      case _            => None
    }
  }

  def isNullaryInvocation(tree: Tree): Boolean =
    tree.symbol != null && tree.symbol.isMethod && (tree match {
      case TypeApply(fun, _) => isNullaryInvocation(fun)
      case tree: RefTree => true
      case _ => false
    })*/



