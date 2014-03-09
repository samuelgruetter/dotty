package dotty.tools.dotc
package core

import Contexts._, Types._, Symbols._, Names._, Flags._, Scopes._
import SymDenotations._
import config.Printers._
import Decorators._
import util.SimpleMap

trait TypeOps { this: Context =>

  final def asSeenFrom(tp: Type, pre: Type, cls: Symbol, theMap: AsSeenFromMap): Type = {

    def toPrefix(pre: Type, cls: Symbol, thiscls: ClassSymbol): Type = /*>|>*/ ctx.debugTraceIndented(s"toPrefix($pre, $cls, $thiscls)") /*<|<*/ {
      if ((pre eq NoType) || (pre eq NoPrefix) || (cls is PackageClass))
        tp
      else if (thiscls.derivesFrom(cls) && pre.baseTypeRef(thiscls).exists)
        pre match {
          case SuperType(thispre, _) => thispre
          case _ => pre
        }
      else
        toPrefix(pre.baseTypeRef(cls).normalizedPrefix, cls.owner, thiscls)
    }

    /*>|>*/ ctx.conditionalTraceIndented(TypeOps.track , s"asSeen ${tp.show} from (${pre.show}, ${cls.show})", show = true) /*<|<*/ { // !!! DEBUG
      tp match {
        case tp: NamedType =>
          val sym = tp.symbol
          if (sym.isStatic) tp
          else tp.derivedSelect(asSeenFrom(tp.prefix, pre, cls, theMap))
        case ThisType(thiscls) =>
          toPrefix(pre, cls, thiscls)
        case _: BoundType | NoPrefix =>
          tp
        case tp: RefinedType =>
          tp.derivedRefinedType(
            asSeenFrom(tp.parent, pre, cls, theMap),
            tp.refinedName,
            asSeenFrom(tp.refinedInfo, pre, cls, theMap))
        case tp: TypeBounds if tp.lo eq tp.hi =>
          tp.derivedTypeAlias(asSeenFrom(tp.lo, pre, cls, theMap))
        case _ =>
          (if (theMap != null) theMap else new AsSeenFromMap(pre, cls))
            .mapOver(tp)
      }
    }
  }

  class AsSeenFromMap(pre: Type, cls: Symbol) extends TypeMap {
    def apply(tp: Type) = asSeenFrom(tp, pre, cls, this)
  }

  /** Implementation of Types#simplified */
  final def simplify(tp: Type, theMap: SimplifyMap): Type = tp match {
    case tp: NamedType =>
      if (tp.symbol.isStatic) tp
      else tp.derivedSelect(simplify(tp.prefix, theMap))
    case tp: PolyParam =>
      typerState.constraint.typeVarOfParam(tp) orElse tp
    case  _: ThisType | _: BoundType | NoPrefix =>
      tp
    case tp: RefinedType =>
      tp.derivedRefinedType(simplify(tp.parent, theMap), tp.refinedName, simplify(tp.refinedInfo, theMap))
    case tp: TypeBounds if tp.lo eq tp.hi =>
      tp.derivedTypeAlias(simplify(tp.lo, theMap))
    case AndType(l, r) =>
      simplify(l, theMap) & simplify(r, theMap)
    case OrType(l, r) =>
      simplify(l, theMap) | simplify(r, theMap)
    case _ =>
      (if (theMap != null) theMap else new SimplifyMap).mapOver(tp)
  }

  class SimplifyMap extends TypeMap {
    def apply(tp: Type) = simplify(tp, this)
  }

  final def isVolatile(tp: Type): Boolean = {

    /** Pre-filter to avoid expensive DNF computation
     *  If needsChecking returns false it is guaranteed that
     *  DNF does not contain intersections, or abstract types with upper
     *  bounds that themselves need checking.
     */
    def needsChecking(tp: Type, isPart: Boolean): Boolean = tp match {
      case tp: TypeRef =>
        tp.info match {
          case TypeBounds(lo, hi) =>
            if (lo eq hi) needsChecking(hi, isPart)
            else isPart || tp.controlled(isVolatile(hi))
          case _ => false
        }
      case tp: RefinedType =>
        needsChecking(tp.parent, true)
      case tp: TypeProxy =>
        needsChecking(tp.underlying, isPart)
      case tp: AndType =>
        true
      case tp: OrType =>
        isPart || needsChecking(tp.tp1, isPart) && needsChecking(tp.tp2, isPart)
      case _ =>
        false
    }

    needsChecking(tp, false) && {
      DNF(tp) forall { case (parents, refinedNames) =>
        val absParents = parents filter (_.symbol is Deferred)
        absParents.nonEmpty && {
          absParents.lengthCompare(2) >= 0 || {
            val ap = absParents.head
            ((parents exists (p =>
              (p ne ap)
              || p.memberNames(abstractTypeNameFilter, tp).nonEmpty
              || p.memberNames(abstractTermNameFilter, tp).nonEmpty))
            || (refinedNames & tp.memberNames(abstractTypeNameFilter, tp)).nonEmpty
            || (refinedNames & tp.memberNames(abstractTermNameFilter, tp)).nonEmpty
            || isVolatile(ap))
          }
        }
      }
    }
  }

  /** The disjunctive normal form of this type.
   *  This collects a set of alternatives, each alternative consisting
   *  of a set of typerefs and a set of refinement names. Both sets are represented
   *  as lists, to obtain a deterministic order. Collected are
   *  all type refs reachable by following aliases and type proxies, and
   *  collecting the elements of conjunctions (&) and disjunctions (|).
   *  The set of refinement names in each alternative
   *  are the set of names in refinement types encountered during the collection.
   */
  final def DNF(tp: Type): List[(List[TypeRef], Set[Name])] = ctx.traceIndented(s"DNF($this)", checks) {
    tp.dealias match {
      case tp: TypeRef =>
        (tp :: Nil, Set[Name]()) :: Nil
      case RefinedType(parent, name) =>
        for ((ps, rs) <- DNF(parent)) yield (ps, rs + name)
      case tp: TypeProxy =>
        DNF(tp.underlying)
      case AndType(l, r) =>
        for ((lps, lrs) <- DNF(l); (rps, rrs) <- DNF(r))
          yield (lps | rps, lrs | rrs)
      case OrType(l, r) =>
        DNF(l) | DNF(r)
      case tp =>
        TypeOps.emptyDNF
    }
  }



  private def enterArgBinding(formal: Symbol, info: Type, cls: ClassSymbol, decls: Scope) = {
    val lazyInfo = new LazyType { // needed so we do not force `formal`.
      def complete(denot: SymDenotation)(implicit ctx: Context): Unit = {
        denot setFlag formal.flags & RetainedTypeArgFlags
        denot.info = info
      }
    }
    val typeArgFlag = if (formal is Local) TypeArgument else EmptyFlags
    val sym = ctx.newSymbol(cls, formal.name, formal.flagsUNSAFE & RetainedTypeArgFlags | typeArgFlag, lazyInfo)
    cls.enter(sym, decls)
  }

  /** If we have member definitions
   *
   *     type argSym v= from
   *     type from v= to
   *
   *  where the variances of both alias are the same, then enter a new definition
   *
   *     type argSym v= to
   *
   *  unless a definition for `argSym` already exists in the current scope.
   */
  def forwardRef(argSym: Symbol, from: Symbol, to: TypeBounds, cls: ClassSymbol, decls: Scope) =
    argSym.info match {
      case info @ TypeBounds(lo2 @ TypeRef(ThisType(_), name), hi2) =>
        if (name == from.name &&
            (lo2 eq hi2) &&
            info.variance == to.variance &&
            !decls.lookup(argSym.name).exists) {
              // println(s"short-circuit ${argSym.name} was: ${argSym.info}, now: $to")
              enterArgBinding(argSym, to, cls, decls)
            }
      case _ =>
    }


  /** Normalize a list of parent types of class `cls` that may contain refinements
   *  to a list of typerefs referring to classes, by converting all refinements to member
   *  definitions in scope `decls`. Can add members to `decls` as a side-effect.
   */
  def normalizeToClassRefs(parents: List[Type], cls: ClassSymbol, decls: Scope): List[TypeRef] = {

    /** If we just entered the type argument binding
     *
     *    type From = To
     *
     *  and there is a type argument binding in a parent in `prefs` of the form
     *
     *    type X = From
     *
     *  then also add the binding
     *
     *    type X = To
     *
     *  to the current scope, provided (1) variances of both aliases are the same, and
     *  (2) X is not yet defined in current scope. This "short-circuiting" prevents
     *  long chains of aliases which would have to be traversed in type comparers.
     */
    def forwardRefs(from: Symbol, to: Type, prefs: List[TypeRef]) = to match {
      case to @ TypeBounds(lo1, hi1) if lo1 eq hi1 =>
        for (pref <- prefs)
          for (argSym <- pref.decls)
            if (argSym is TypeArgument)
              forwardRef(argSym, from, to, cls, decls)
      case _ =>
    }

    // println(s"normalizing $parents of $cls in ${cls.owner}") // !!! DEBUG
    var refinements: SimpleMap[TypeName, Type] = SimpleMap.Empty
    var formals: SimpleMap[TypeName, Symbol] = SimpleMap.Empty
    def normalizeToRef(tp: Type): TypeRef = tp match {
      case tp @ RefinedType(tp1, name: TypeName) =>
        val prevInfo = refinements(name)
        refinements = refinements.updated(name,
            if (prevInfo == null) tp.refinedInfo else prevInfo & tp.refinedInfo)
        formals = formals.updated(name, tp1.typeParamNamed(name))
        normalizeToRef(tp1)
      case tp: TypeRef =>
        if (tp.symbol.info.isAlias) normalizeToRef(tp.info.bounds.hi)
        else tp
      case ErrorType =>
        defn.AnyClass.typeRef
      case _ =>
        throw new TypeError(s"unexpected parent type: $tp")
    }
    val parentRefs = parents map normalizeToRef
    refinements foreachBinding { (name, refinedInfo) =>
      assert(decls.lookup(name) == NoSymbol, // DEBUG
        s"redefinition of ${decls.lookup(name).debugString} in ${cls.showLocated}")
      enterArgBinding(formals(name), refinedInfo, cls, decls)
    }
    // These two loops cannot be fused because second loop assumes that
    // all arguments have been entered in `decls`.
    refinements foreachBinding { (name, refinedInfo) =>
      forwardRefs(formals(name), refinedInfo, parentRefs)
    }
    parentRefs
  }
}

object TypeOps {
  val emptyDNF = (Nil, Set[Name]()) :: Nil
  var track = false // !!!DEBUG
}
