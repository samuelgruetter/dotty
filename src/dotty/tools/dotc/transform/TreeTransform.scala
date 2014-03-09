package dotty.tools.dotc.transform

import dotty.tools.dotc.ast.tpd
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.core.Phases.Phase
import dotty.tools.dotc.ast.Trees._
import java.util
import scala.annotation.tailrec

object TreeTransforms {

  /** The base class of tree transforms. For each kind of tree K, there are
    * two methods which can be overridden:
    *
    * prepareForK // return a new TreeTransform which gets applied to the K
    * // node and its children
    * transformK // transform node of type K
    *
    * If a transform does not need to visit a node or any of its children, it
    * signals this fact by returning a NoTransform from a prepare method.
    *
    * If all transforms in a group are NoTransforms, the tree is no longer traversed.
    *
    * @param group The group this transform belongs to
    * @param idx The index of this transform in its group
    *
    *            Performance analysis: Taking the dotty compiler frontend as a use case, we are aiming for a warm performance of
    *            about 4000 lines / sec. This means 6 seconds for a codebase of 24'000 lines. Of these the frontend consumes
    *            over 2.5 seconds, erasure and code generation will most likely consume over 1 second each. So we would have
    *            about 1 sec for all other transformations in our budget. Of this second, let's assume a maximum of 20% for
    *            the general dispatch overhead as opposed to the concrete work done in transformations. So that leaves us with
    *            0.2sec, or roughly 600M processor cycles.
    *
    *            Now, to the amount of work that needs to be done. The codebase produces of about 250'000 trees after typechecking.
    *            Transformations are likely to make this bigger so let's assume 300K trees on average. We estimate to have about 100
    *            micro-transformations. Let's say 5 transformation groups of 20 micro-transformations each. (by comparison,
    *            scalac has in excess of 20 phases, and most phases do multiple transformations). There are then 30M visits
    *            of a node by a transformation. Each visit has a budget of 20 processor cycles.
    *
    *            A more detailed breakdown: I assume that about one third of all transformations have real work to do for each node.
    *            This might look high, but keep in mind that the most common nodes are Idents and Selects, and most transformations
    *            touch these. By contrast the amount of work for generating new transformations should be negligible.
    *
    *            So, in 400 clock cycles we need to (1) perform a pattern match according to the type of node, (2) generate new
    *            transformations if applicable, (3) reconstitute the tree node from the result of transforming the children, and
    *            (4) chain 7 out of 20 transformations over the resulting tree node. I believe the current algorithm is suitable
    *            for achieving this goal, but there can be no wasted cycles anywhere.
    */
  class TreeTransform(group: TreeTransformer, idx: Int) {

    import tpd._
    import group._

    def prepareForIdent(tree: Ident) = this
    def prepareForSelect(tree: Select) = this
    def prepareForThis(tree: This) = this
    def prepareForSuper(tree: Super) = this
    def prepareForApply(tree: Apply) = this
    def prepareForTypeApply(tree: TypeApply) = this
    def prepareForLiteral(tree: Literal) = this
    def prepareForPair(tree: Pair) = this
    def prepareForNew(tree: New) = this
    def prepareForTyped(tree: Typed) = this
    def prepareForAssign(tree: Assign) = this
    def prepareForBlock(tree: Block) = this
    def prepareForIf(tree: If) = this
    def prepareForClosure(tree: Closure) = this
    def prepareForMatch(tree: Match) = this
    def prepareForCaseDef(tree: CaseDef) = this
    def prepareForReturn(tree: Return) = this
    def prepareForTry(tree: Try) = this
    def prepareForThrow(tree: Throw) = this
    def prepareForSeqLiteral(tree: SeqLiteral) = this
    def prepareForTypeTree(tree: TypeTree) = this
    def prepareForSelectFromTypeTree(tree: SelectFromTypeTree) = this
    def prepareForBind(tree: Bind) = this
    def prepareForAlternative(tree: Alternative) = this
    def prepareForTypeDef(tree: TypeDef) = this
    def prepareForUnApply(tree: UnApply) = this
    def prepareForValDef(tree: ValDef) = this
    def prepareForDefDef(tree: DefDef) = this
    def prepareForTemplate(tree: Template) = this
    def prepareForPackageDef(tree: PackageDef) = this

    def transformIdent(tree: Ident)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformSelect(tree: Select)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformThis(tree: This)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformSuper(tree: Super)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformApply(tree: Apply)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformTypeApply(tree: TypeApply)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformLiteral(tree: Literal)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformNew(tree: New)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformPair(tree: Pair)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformTyped(tree: Typed)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformAssign(tree: Assign)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformBlock(tree: Block)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformIf(tree: If)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformClosure(tree: Closure)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformMatch(tree: Match)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformCaseDef(tree: CaseDef)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformReturn(tree: Return)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformTry(tree: Try)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformThrow(tree: Throw)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformSeqLiteral(tree: SeqLiteral)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformTypeTree(tree: TypeTree)(implicit ctx: Context): Tree = tree
    def transformSelectFromTypeTree(tree: SelectFromTypeTree)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformBind(tree: Bind)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformAlternative(tree: Alternative)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformUnApply(tree: UnApply)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformValDef(tree: ValDef)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformDefDef(tree: DefDef)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformTypeDef(tree: TypeDef)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformTemplate(tree: Template)(implicit ctx: Context, info: TransformerInfo): Tree = tree
    def transformPackageDef(tree: PackageDef)(implicit ctx: Context, info: TransformerInfo): Tree = tree

    /** Transform tree using all transforms of current group (including this one) */
    def transform(tree: Tree)(implicit ctx: Context): Tree = group.transform(tree)

    /** Transform subtree using all transforms following the current one in this group */
    def transformFollowingDeep(tree: Tree)(implicit ctx: Context, info: TransformerInfo): Tree = group.transform(tree, info, idx + 1)

    /** Transform single node using all transforms following the current one in this group */
    def transformFollowing(tree: Tree)(implicit ctx: Context, info: TransformerInfo): Tree = group.transformSingle(tree, idx + 1)
  }

  val NoTransform = new TreeTransform(null, -1)

  type Mutator = (TreeTransform, tpd.Tree) => TreeTransform

  class TransformerInfo(val transformers: Array[TreeTransform], val nx: NXTransformations) {}

  /**
   * This class maintains track of which methods are redefined in MiniPhases and creates execution plans for transformXXX and prepareXXX
   * Thanks to Martin for this idea
   * @see NXTransformations.index for format of plan
   */
  class NXTransformations {

    private def hasRedefinedMethod(cls: Class[_], name: String): Boolean =
      if (cls.getDeclaredMethods.exists(_.getName == name)) cls != classOf[TreeTransform]
      else hasRedefinedMethod(cls.getSuperclass, name)

    /** Create an index array `next` of size one larger than teh size of `transforms` such that
      * for each index i, `next(i)` is the smallest index j such that
      *
      * i <= j
      * j == transforms.length || transform(j) defines a non-default method with given `name`
      */
    private def index(transformations: Array[TreeTransform], name: String): Array[Int] = {
      val len = transformations.length
      val next = new Array[Int](len + 1)
      var nextTransform: Int = len

      /* loop invariant: nextTransform == the smallest j such that
       * i < j and
       * j == transforms.length || transform(j) defines a non-default method with given `name`
       */
      next(len) = len
      var i = len - 1
      while (i >= 0) {
        // update nextTransform if this phase redefines the method
        if (hasRedefinedMethod(transformations(i).getClass, name)) {
          nextTransform = i
        }
        next(i) = nextTransform
        i -= 1
      }
      next
    }

    private def indexUpdate(prev: Array[Int], changedTansformation: TreeTransform, index: Int, name: String, copy: Boolean = true) = {
      val isDefinedNow = hasRedefinedMethod(changedTansformation.getClass, name)
      val wasDefinedBefore = prev(index) == index
      if (isDefinedNow == wasDefinedBefore) prev
      else {
        val result = if (copy) prev.clone() else prev
        val oldValue = result(index)
        val newValue =
          if (wasDefinedBefore /* && !isDefinedNow */ ) prev(index + 1)
          else index // isDefinedNow
        var i = index
        while (i >= 0 && result(i) == oldValue) {
          result(i) = newValue
          i -= 1
        }
        result
      }
    }

    def this(transformations: Array[TreeTransform]) = {
      this()
      nxPrepIdent = index(transformations, "prepareForIdent")
      nxPrepSelect = index(transformations, "prepareForSelect")
      nxPrepThis = index(transformations, "prepareForThis")
      nxPrepSuper = index(transformations, "prepareForSuper")
      nxPrepApply = index(transformations, "prepareForApply")
      nxPrepTypeApply = index(transformations, "prepareForTypeApply")
      nxPrepLiteral = index(transformations, "prepareForLiteral")
      nxPrepNew = index(transformations, "prepareForNew")
      nxPrepPair = index(transformations, "prepareForPair")
      nxPrepTyped = index(transformations, "prepareForTyped")
      nxPrepAssign = index(transformations, "prepareForAssign")
      nxPrepBlock = index(transformations, "prepareForBlock")
      nxPrepIf = index(transformations, "prepareForIf")
      nxPrepClosure = index(transformations, "prepareForClosure")
      nxPrepCaseDef = index(transformations, "prepareForCaseDef")
      nxPrepMatch = index(transformations, "prepareForMatch")
      nxPrepReturn = index(transformations, "prepareForReturn")
      nxPrepTry = index(transformations, "prepareForTry")
      nxPrepThrow = index(transformations, "prepareForThrow")
      nxPrepSeqLiteral = index(transformations, "prepareForSeqLiteral")
      nxPrepTypeTree = index(transformations, "prepareForTypeTree")
      nxPrepSelectFromTypeTree = index(transformations, "prepareForSelectFromTypeTree")
      nxPrepBind = index(transformations, "prepareForBind")
      nxPrepAlternative = index(transformations, "prepareForAlternative")
      nxPrepUnApply = index(transformations, "prepareForUnApply")
      nxPrepValDef = index(transformations, "prepareForValDef")
      nxPrepDefDef = index(transformations, "prepareForDefDef")
      nxPrepTypeDef = index(transformations, "prepareForTypeDef")
      nxPrepTemplate = index(transformations, "prepareForTemplate")
      nxPrepPackageDef = index(transformations, "prepareForPackageDef")

      nxTransIdent = index(transformations, "transformIdent")
      nxTransSelect = index(transformations, "transformSelect")
      nxTransThis = index(transformations, "transformThis")
      nxTransSuper = index(transformations, "transformSuper")
      nxTransApply = index(transformations, "transformApply")
      nxTransTypeApply = index(transformations, "transformTypeApply")
      nxTransLiteral = index(transformations, "transformLiteral")
      nxTransNew = index(transformations, "transformNew")
      nxTransPair = index(transformations, "transformPair")
      nxTransTyped = index(transformations, "transformTyped")
      nxTransAssign = index(transformations, "transformAssign")
      nxTransBlock = index(transformations, "transformBlock")
      nxTransIf = index(transformations, "transformIf")
      nxTransClosure = index(transformations, "transformClosure")
      nxTransMatch = index(transformations, "transformMatch")
      nxTransCaseDef = index(transformations, "transformCaseDef")
      nxTransReturn = index(transformations, "transformReturn")
      nxTransTry = index(transformations, "transformTry")
      nxTransThrow = index(transformations, "transformThrow")
      nxTransSeqLiteral = index(transformations, "transformSeqLiteral")
      nxTransTypeTree = index(transformations, "transformTypeTree")
      nxTransSelectFromTypeTree = index(transformations, "transformSelectFromTypeTree")
      nxTransBind = index(transformations, "transformBind")
      nxTransAlternative = index(transformations, "transformAlternative")
      nxTransUnApply = index(transformations, "transformUnApply")
      nxTransValDef = index(transformations, "transformValDef")
      nxTransDefDef = index(transformations, "transformDefDef")
      nxTransTypeDef = index(transformations, "transformTypeDef")
      nxTransTemplate = index(transformations, "transformTemplate")
      nxTransPackageDef = index(transformations, "transformPackageDef")
    }

    def this(prev: NXTransformations, changedTansformation: TreeTransform, transformationIndex: Int, reuse: Boolean = false) = {
      this()
      val copy = !reuse
      nxPrepIdent = indexUpdate(prev.nxPrepIdent, changedTansformation, transformationIndex, "prepareForIdent", copy)
      nxPrepSelect = indexUpdate(prev.nxPrepSelect, changedTansformation, transformationIndex, "prepareForSelect", copy)
      nxPrepThis = indexUpdate(prev.nxPrepThis, changedTansformation, transformationIndex, "prepareForThis", copy)
      nxPrepSuper = indexUpdate(prev.nxPrepSuper, changedTansformation, transformationIndex, "prepareForSuper", copy)
      nxPrepApply = indexUpdate(prev.nxPrepApply, changedTansformation, transformationIndex, "prepareForApply", copy)
      nxPrepTypeApply = indexUpdate(prev.nxPrepTypeApply, changedTansformation, transformationIndex, "prepareForTypeApply", copy)
      nxPrepLiteral = indexUpdate(prev.nxPrepLiteral, changedTansformation, transformationIndex, "prepareForLiteral", copy)
      nxPrepNew = indexUpdate(prev.nxPrepNew, changedTansformation, transformationIndex, "prepareForNew", copy)
      nxPrepPair = indexUpdate(prev.nxPrepPair, changedTansformation, transformationIndex, "prepareForPair", copy)
      nxPrepTyped = indexUpdate(prev.nxPrepTyped, changedTansformation, transformationIndex, "prepareForTyped", copy)
      nxPrepAssign = indexUpdate(prev.nxPrepAssign, changedTansformation, transformationIndex, "prepareForAssign", copy)
      nxPrepBlock = indexUpdate(prev.nxPrepBlock, changedTansformation, transformationIndex, "prepareForBlock", copy)
      nxPrepIf = indexUpdate(prev.nxPrepIf, changedTansformation, transformationIndex, "prepareForIf", copy)
      nxPrepClosure = indexUpdate(prev.nxPrepClosure, changedTansformation, transformationIndex, "prepareForClosure", copy)
      nxPrepMatch = indexUpdate(prev.nxPrepMatch, changedTansformation, transformationIndex, "prepareForMatch", copy)
      nxPrepCaseDef = indexUpdate(prev.nxPrepCaseDef, changedTansformation, transformationIndex, "prepareForCaseDef", copy)
      nxPrepReturn = indexUpdate(prev.nxPrepReturn, changedTansformation, transformationIndex, "prepareForReturn", copy)
      nxPrepTry = indexUpdate(prev.nxPrepTry, changedTansformation, transformationIndex, "prepareForTry", copy)
      nxPrepThrow = indexUpdate(prev.nxPrepThrow, changedTansformation, transformationIndex, "prepareForThrow", copy)
      nxPrepSeqLiteral = indexUpdate(prev.nxPrepSeqLiteral, changedTansformation, transformationIndex, "prepareForSeqLiteral", copy)
      nxPrepTypeTree = indexUpdate(prev.nxPrepTypeTree, changedTansformation, transformationIndex, "prepareForTypeTree", copy)
      nxPrepSelectFromTypeTree = indexUpdate(prev.nxPrepSelectFromTypeTree, changedTansformation, transformationIndex, "prepareForSelectFromTypeTree", copy)
      nxPrepBind = indexUpdate(prev.nxPrepBind, changedTansformation, transformationIndex, "prepareForBind", copy)
      nxPrepAlternative = indexUpdate(prev.nxPrepAlternative, changedTansformation, transformationIndex, "prepareForAlternative", copy)
      nxPrepUnApply = indexUpdate(prev.nxPrepUnApply, changedTansformation, transformationIndex, "prepareForUnApply", copy)
      nxPrepValDef = indexUpdate(prev.nxPrepValDef, changedTansformation, transformationIndex, "prepareForValDef", copy)
      nxPrepDefDef = indexUpdate(prev.nxPrepDefDef, changedTansformation, transformationIndex, "prepareForDefDef", copy)
      nxPrepTypeDef = indexUpdate(prev.nxPrepTypeDef, changedTansformation, transformationIndex, "prepareForTypeDef", copy)
      nxPrepTemplate = indexUpdate(prev.nxPrepTemplate, changedTansformation, transformationIndex, "prepareForTemplate", copy)
      nxPrepPackageDef = indexUpdate(prev.nxPrepPackageDef, changedTansformation, transformationIndex, "prepareForPackageDef", copy)

      nxTransIdent = indexUpdate(prev.nxTransIdent, changedTansformation, transformationIndex, "transformIdent", copy)
      nxTransSelect = indexUpdate(prev.nxTransSelect, changedTansformation, transformationIndex, "transformSelect", copy)
      nxTransThis = indexUpdate(prev.nxTransThis, changedTansformation, transformationIndex, "transformThis", copy)
      nxTransSuper = indexUpdate(prev.nxTransSuper, changedTansformation, transformationIndex, "transformSuper", copy)
      nxTransApply = indexUpdate(prev.nxTransApply, changedTansformation, transformationIndex, "transformApply", copy)
      nxTransTypeApply = indexUpdate(prev.nxTransTypeApply, changedTansformation, transformationIndex, "transformTypeApply", copy)
      nxTransLiteral = indexUpdate(prev.nxTransLiteral, changedTansformation, transformationIndex, "transformLiteral", copy)
      nxTransNew = indexUpdate(prev.nxTransNew, changedTansformation, transformationIndex, "transformNew", copy)
      nxTransPair = indexUpdate(prev.nxTransPair, changedTansformation, transformationIndex, "transformPair", copy)
      nxTransTyped = indexUpdate(prev.nxTransTyped, changedTansformation, transformationIndex, "transformTyped", copy)
      nxTransAssign = indexUpdate(prev.nxTransAssign, changedTansformation, transformationIndex, "transformAssign", copy)
      nxTransBlock = indexUpdate(prev.nxTransBlock, changedTansformation, transformationIndex, "transformBlock", copy)
      nxTransIf = indexUpdate(prev.nxTransIf, changedTansformation, transformationIndex, "transformIf", copy)
      nxTransClosure = indexUpdate(prev.nxTransClosure, changedTansformation, transformationIndex, "transformClosure", copy)
      nxTransMatch = indexUpdate(prev.nxTransMatch, changedTansformation, transformationIndex, "transformMatch", copy)
      nxTransCaseDef = indexUpdate(prev.nxTransCaseDef, changedTansformation, transformationIndex, "transformCaseDef", copy)
      nxTransReturn = indexUpdate(prev.nxTransReturn, changedTansformation, transformationIndex, "transformReturn", copy)
      nxTransTry = indexUpdate(prev.nxTransTry, changedTansformation, transformationIndex, "transformTry", copy)
      nxTransThrow = indexUpdate(prev.nxTransThrow, changedTansformation, transformationIndex, "transformThrow", copy)
      nxTransSeqLiteral = indexUpdate(prev.nxTransSeqLiteral, changedTansformation, transformationIndex, "transformSeqLiteral", copy)
      nxTransTypeTree = indexUpdate(prev.nxTransTypeTree, changedTansformation, transformationIndex, "transformTypeTree", copy)
      nxTransSelectFromTypeTree = indexUpdate(prev.nxTransSelectFromTypeTree, changedTansformation, transformationIndex, "transformSelectFromTypeTree", copy)
      nxTransBind = indexUpdate(prev.nxTransBind, changedTansformation, transformationIndex, "transformBind", copy)
      nxTransAlternative = indexUpdate(prev.nxTransAlternative, changedTansformation, transformationIndex, "transformAlternative", copy)
      nxTransUnApply = indexUpdate(prev.nxTransUnApply, changedTansformation, transformationIndex, "transformUnApply", copy)
      nxTransValDef = indexUpdate(prev.nxTransValDef, changedTansformation, transformationIndex, "transformValDef", copy)
      nxTransDefDef = indexUpdate(prev.nxTransDefDef, changedTansformation, transformationIndex, "transformDefDef", copy)
      nxTransTypeDef = indexUpdate(prev.nxTransTypeDef, changedTansformation, transformationIndex, "transformTypeDef", copy)
      nxTransTemplate = indexUpdate(prev.nxTransTemplate, changedTansformation, transformationIndex, "transformTemplate", copy)
      nxTransPackageDef = indexUpdate(prev.nxTransPackageDef, changedTansformation, transformationIndex, "transformPackageDef", copy)
    }

    var nxPrepIdent: Array[Int] = _
    var nxPrepSelect: Array[Int] = _
    var nxPrepThis: Array[Int] = _
    var nxPrepSuper: Array[Int] = _
    var nxPrepApply: Array[Int] = _
    var nxPrepTypeApply: Array[Int] = _
    var nxPrepLiteral: Array[Int] = _
    var nxPrepNew: Array[Int] = _
    var nxPrepPair: Array[Int] = _
    var nxPrepTyped: Array[Int] = _
    var nxPrepAssign: Array[Int] = _
    var nxPrepBlock: Array[Int] = _
    var nxPrepIf: Array[Int] = _
    var nxPrepClosure: Array[Int] = _
    var nxPrepMatch: Array[Int] = _
    var nxPrepCaseDef: Array[Int] = _
    var nxPrepReturn: Array[Int] = _
    var nxPrepTry: Array[Int] = _
    var nxPrepThrow: Array[Int] = _
    var nxPrepSeqLiteral: Array[Int] = _
    var nxPrepTypeTree: Array[Int] = _
    var nxPrepSelectFromTypeTree: Array[Int] = _
    var nxPrepBind: Array[Int] = _
    var nxPrepAlternative: Array[Int] = _
    var nxPrepUnApply: Array[Int] = _
    var nxPrepValDef: Array[Int] = _
    var nxPrepDefDef: Array[Int] = _
    var nxPrepTypeDef: Array[Int] = _
    var nxPrepTemplate: Array[Int] = _
    var nxPrepPackageDef: Array[Int] = _

    var nxTransIdent: Array[Int] = _
    var nxTransSelect: Array[Int] = _
    var nxTransThis: Array[Int] = _
    var nxTransSuper: Array[Int] = _
    var nxTransApply: Array[Int] = _
    var nxTransTypeApply: Array[Int] = _
    var nxTransLiteral: Array[Int] = _
    var nxTransNew: Array[Int] = _
    var nxTransPair: Array[Int] = _
    var nxTransTyped: Array[Int] = _
    var nxTransAssign: Array[Int] = _
    var nxTransBlock: Array[Int] = _
    var nxTransIf: Array[Int] = _
    var nxTransClosure: Array[Int] = _
    var nxTransMatch: Array[Int] = _
    var nxTransCaseDef: Array[Int] = _
    var nxTransReturn: Array[Int] = _
    var nxTransTry: Array[Int] = _
    var nxTransThrow: Array[Int] = _
    var nxTransSeqLiteral: Array[Int] = _
    var nxTransTypeTree: Array[Int] = _
    var nxTransSelectFromTypeTree: Array[Int] = _
    var nxTransBind: Array[Int] = _
    var nxTransAlternative: Array[Int] = _
    var nxTransUnApply: Array[Int] = _
    var nxTransValDef: Array[Int] = _
    var nxTransDefDef: Array[Int] = _
    var nxTransTypeDef: Array[Int] = _
    var nxTransTemplate: Array[Int] = _
    var nxTransPackageDef: Array[Int] = _
  }

  /** A group of tree transforms that are applied in sequence during the same phase */
  abstract class TreeTransformer extends Phase {

    import tpd.{TreeTransformer => _, _}

    protected def transformations: Array[(TreeTransformer, Int) => TreeTransform]

    private val processedTrees = new util.IdentityHashMap[Tree, Tree]()

    override def run(implicit ctx: Context): Unit = {
      val curTree = ctx.compilationUnit.tpdTree
      val newTree = transform(curTree)
      ctx.compilationUnit.tpdTree = newTree
    }

    def mutateTransformers(info: TransformerInfo, mutator: Mutator, mutationPlan: Array[Int], tree: Tree, cur: Int) = {
      var transformersCopied = false
      var nxCopied = false
      var result = info.transformers
      var resultNX = info.nx
      var i = mutationPlan(cur)
      val l = result.length
      var allDone = i < l
      while (i < l) {
        val oldT = result(i)
        val newT = mutator(oldT, tree)
        allDone = allDone && (newT eq NoTransform)
        if (!(oldT eq newT)) {
          if (!transformersCopied) result = result.clone()
          transformersCopied = true
          result(i) = newT
          if (!(newT.getClass == oldT.getClass)) {
            resultNX = new NXTransformations(resultNX, newT, i, nxCopied)
            nxCopied = true
          }
        }
        i = mutationPlan(i + 1)
      }
      if (allDone) null
      else if (!transformersCopied) info
      else new TransformerInfo(result, resultNX)
    }

    val prepForIdent: Mutator = (trans, tree) => trans.prepareForIdent(tree.asInstanceOf[Ident])
    val prepForSelect: Mutator = (trans, tree) => trans.prepareForSelect(tree.asInstanceOf[Select])
    val prepForThis: Mutator = (trans, tree) => trans.prepareForThis(tree.asInstanceOf[This])
    val prepForSuper: Mutator = (trans, tree) => trans.prepareForSuper(tree.asInstanceOf[Super])
    val prepForApply: Mutator = (trans, tree) => trans.prepareForApply(tree.asInstanceOf[Apply])
    val prepForTypeApply: Mutator = (trans, tree) => trans.prepareForTypeApply(tree.asInstanceOf[TypeApply])
    val prepForNew: Mutator = (trans, tree) => trans.prepareForNew(tree.asInstanceOf[New])
    val prepForPair: Mutator = (trans, tree) => trans.prepareForPair(tree.asInstanceOf[Pair])
    val prepForTyped: Mutator = (trans, tree) => trans.prepareForTyped(tree.asInstanceOf[Typed])
    val prepForAssign: Mutator = (trans, tree) => trans.prepareForAssign(tree.asInstanceOf[Assign])
    val prepForLiteral: Mutator = (trans, tree) => trans.prepareForLiteral(tree.asInstanceOf[Literal])
    val prepForBlock: Mutator = (trans, tree) => trans.prepareForBlock(tree.asInstanceOf[Block])
    val prepForIf: Mutator = (trans, tree) => trans.prepareForIf(tree.asInstanceOf[If])
    val prepForClosure: Mutator = (trans, tree) => trans.prepareForClosure(tree.asInstanceOf[Closure])
    val prepForMatch: Mutator = (trans, tree) => trans.prepareForMatch(tree.asInstanceOf[Match])
    val prepForCaseDef: Mutator = (trans, tree) => trans.prepareForCaseDef(tree.asInstanceOf[CaseDef])
    val prepForReturn: Mutator = (trans, tree) => trans.prepareForReturn(tree.asInstanceOf[Return])
    val prepForTry: Mutator = (trans, tree) => trans.prepareForTry(tree.asInstanceOf[Try])
    val prepForThrow: Mutator = (trans, tree) => trans.prepareForThrow(tree.asInstanceOf[Throw])
    val prepForSeqLiteral: Mutator = (trans, tree) => trans.prepareForSeqLiteral(tree.asInstanceOf[SeqLiteral])
    val prepForTypeTree: Mutator = (trans, tree) => trans.prepareForTypeTree(tree.asInstanceOf[TypeTree])
    val prepForSelectFromTypeTree: Mutator = (trans, tree) => trans.prepareForSelectFromTypeTree(tree.asInstanceOf[SelectFromTypeTree])
    val prepForBind: Mutator = (trans, tree) => trans.prepareForBind(tree.asInstanceOf[Bind])
    val prepForAlternative: Mutator = (trans, tree) => trans.prepareForAlternative(tree.asInstanceOf[Alternative])
    val prepForUnApply: Mutator = (trans, tree) => trans.prepareForUnApply(tree.asInstanceOf[UnApply])
    val prepForValDef: Mutator = (trans, tree) => trans.prepareForValDef(tree.asInstanceOf[ValDef])
    val prepForDefDef: Mutator = (trans, tree) => trans.prepareForDefDef(tree.asInstanceOf[DefDef])
    val prepForTypeDef: Mutator = (trans, tree) => trans.prepareForTypeDef(tree.asInstanceOf[TypeDef])
    val prepForTemplate: Mutator = (trans, tree) => trans.prepareForTemplate(tree.asInstanceOf[Template])
    val prepForPackageDef: Mutator = (trans, tree) => trans.prepareForPackageDef(tree.asInstanceOf[PackageDef])

    def transform(t: Tree)(implicit ctx: Context): Tree = {
      val initialTransformations = transformations.zipWithIndex.map(x => x._1(this, x._2))
      transform(t, new TransformerInfo(initialTransformations, new NXTransformations(initialTransformations)), 0)
    }

    @tailrec
    final private[TreeTransforms] def goIdent(tree: Ident, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformIdent(tree) match {
          case t: Ident => goIdent(t, info.nx.nxTransIdent(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goSelect(tree: Select, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformSelect(tree) match {
          case t: Select => goSelect(t, info.nx.nxTransSelect(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goThis(tree: This, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformThis(tree) match {
          case t: This => goThis(t, info.nx.nxTransThis(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goSuper(tree: Super, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformSuper(tree) match {
          case t: Super => goSuper(t, info.nx.nxTransSuper(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goApply(tree: Apply, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformApply(tree) match {
          case t: Apply => goApply(t, info.nx.nxTransApply(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goTypeApply(tree: TypeApply, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformTypeApply(tree) match {
          case t: TypeApply => goTypeApply(t, info.nx.nxTransTypeApply(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goNew(tree: New, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformNew(tree) match {
          case t: New => goNew(t, info.nx.nxTransNew(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goPair(tree: Pair, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformPair(tree) match {
          case t: Pair => goPair(t, info.nx.nxTransPair(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goTyped(tree: Typed, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformTyped(tree) match {
          case t: Typed => goTyped(t, info.nx.nxTransTyped(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goAssign(tree: Assign, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformAssign(tree) match {
          case t: Assign => goAssign(t, info.nx.nxTransAssign(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goLiteral(tree: Literal, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformLiteral(tree) match {
          case t: Literal => goLiteral(t, info.nx.nxTransLiteral(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goBlock(tree: Block, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformBlock(tree) match {
          case t: Block => goBlock(t, info.nx.nxTransBlock(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goIf(tree: If, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformIf(tree) match {
          case t: If => goIf(t, info.nx.nxTransIf(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goClosure(tree: Closure, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformClosure(tree) match {
          case t: Closure => goClosure(t, info.nx.nxTransClosure(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goMatch(tree: Match, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformMatch(tree) match {
          case t: Match => goMatch(t, info.nx.nxTransMatch(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goCaseDef(tree: CaseDef, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformCaseDef(tree) match {
          case t: CaseDef => goCaseDef(t, info.nx.nxTransCaseDef(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goReturn(tree: Return, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformReturn(tree) match {
          case t: Return => goReturn(t, info.nx.nxTransReturn(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goTry(tree: Try, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformTry(tree) match {
          case t: Try => goTry(t, info.nx.nxTransTry(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goThrow(tree: Throw, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformThrow(tree) match {
          case t: Throw => goThrow(t, info.nx.nxTransThrow(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goSeqLiteral(tree: SeqLiteral, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformSeqLiteral(tree) match {
          case t: SeqLiteral => goSeqLiteral(t, info.nx.nxTransSeqLiteral(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goTypeTree(tree: TypeTree, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformTypeTree(tree) match {
          case t: TypeTree => goTypeTree(t, info.nx.nxTransTypeTree(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goSelectFromTypeTree(tree: SelectFromTypeTree, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformSelectFromTypeTree(tree) match {
          case t: SelectFromTypeTree => goSelectFromTypeTree(t, info.nx.nxTransSelectFromTypeTree(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goBind(tree: Bind, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformBind(tree) match {
          case t: Bind => goBind(t, info.nx.nxTransBind(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goAlternative(tree: Alternative, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformAlternative(tree) match {
          case t: Alternative => goAlternative(t, info.nx.nxTransAlternative(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goValDef(tree: ValDef, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformValDef(tree) match {
          case t: ValDef => goValDef(t, info.nx.nxTransValDef(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goDefDef(tree: DefDef, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformDefDef(tree) match {
          case t: DefDef => goDefDef(t, info.nx.nxTransDefDef(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goUnApply(tree: UnApply, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformUnApply(tree) match {
          case t: UnApply => goUnApply(t, info.nx.nxTransUnApply(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goTypeDef(tree: TypeDef, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformTypeDef(tree) match {
          case t: TypeDef => goTypeDef(t, info.nx.nxTransTypeDef(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goTemplate(tree: Template, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformTemplate(tree) match {
          case t: Template => goTemplate(t, info.nx.nxTransTemplate(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    @tailrec
    final private[TreeTransforms] def goPackageDef(tree: PackageDef, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree = {
      if (cur < info.transformers.length) {
        info.transformers(cur).transformPackageDef(tree) match {
          case t: PackageDef => goPackageDef(t, info.nx.nxTransPackageDef(cur + 1))
          case t => transformSingle(t, cur + 1)
        }
      } else tree
    }

    final private[TreeTransforms] def goNamed(tree: NameTree, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree =
      tree match {
        case tree: Ident => goIdent(tree, info.nx.nxTransIdent(cur))
        case tree: Select => goSelect(tree, info.nx.nxTransSelect(cur))
        case tree: SelectFromTypeTree =>
          goSelectFromTypeTree(tree, info.nx.nxTransSelectFromTypeTree(cur))
        case tree: Bind => goBind(tree, cur)
        case tree: ValDef if !tree.isEmpty => goValDef(tree, info.nx.nxTransValDef(cur))
        case tree: DefDef => goDefDef(tree, info.nx.nxTransDefDef(cur))
        case tree: TypeDef => goTypeDef(tree, info.nx.nxTransTypeDef(cur))
        case _ => tree
      }

    final private[TreeTransforms] def goUnamed(tree: Tree, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree =
      tree match {
        case tree: This => goThis(tree, info.nx.nxTransThis(cur))
        case tree: Super => goSuper(tree, info.nx.nxTransSuper(cur))
        case tree: Apply => goApply(tree, info.nx.nxTransApply(cur))
        case tree: TypeApply => goTypeApply(tree, info.nx.nxTransTypeApply(cur))
        case tree: Literal => goLiteral(tree, info.nx.nxTransLiteral(cur))
        case tree: New => goNew(tree, info.nx.nxTransNew(cur))
        case tree: Pair => goPair(tree, info.nx.nxTransPair(cur))
        case tree: Typed => goTyped(tree, info.nx.nxTransTyped(cur))
        case tree: Assign => goAssign(tree, info.nx.nxTransAssign(cur))
        case tree: Block => goBlock(tree, info.nx.nxTransBlock(cur))
        case tree: If => goIf(tree, info.nx.nxTransIf(cur))
        case tree: Closure => goClosure(tree, info.nx.nxTransClosure(cur))
        case tree: Match => goMatch(tree, info.nx.nxTransMatch(cur))
        case tree: CaseDef => goCaseDef(tree, info.nx.nxTransCaseDef(cur))
        case tree: Return => goReturn(tree, info.nx.nxTransReturn(cur))
        case tree: Try => goTry(tree, info.nx.nxTransTry(cur))
        case tree: Throw => goThrow(tree, info.nx.nxTransThrow(cur))
        case tree: SeqLiteral => goSeqLiteral(tree, info.nx.nxTransLiteral(cur))
        case tree: TypeTree => goTypeTree(tree, info.nx.nxTransTypeTree(cur))
        case tree: Alternative => goAlternative(tree, info.nx.nxTransAlternative(cur))
        case tree: UnApply => goUnApply(tree, info.nx.nxTransUnApply(cur))
        case tree: Template => goTemplate(tree, info.nx.nxTransTemplate(cur))
        case tree: PackageDef => goPackageDef(tree, info.nx.nxTransPackageDef(cur))
        case Thicket(trees) if trees != Nil =>
          val trees1 = transformL(trees.asInstanceOf[List[tpd.Tree]], info, cur)
          if (trees1 eq trees) tree else Thicket(trees1)
        case tree => tree
      }

    final private[TreeTransforms] def transformSingle(tree: Tree, cur: Int)(implicit ctx: Context, info: TransformerInfo): Tree =
      tree match {
        // split one big match into 2 smaller ones
        case tree: NameTree => goNamed(tree, cur)
        case tree => goUnamed(tree, cur)
      }

    final private[TreeTransforms] def transformNameTree(tree: NameTree, info: TransformerInfo, cur: Int)(implicit ctx: Context): Tree =
      tree match {
        case tree: Ident =>
          implicit val mutatedInfo = mutateTransformers(info, prepForIdent, info.nx.nxPrepIdent, tree, cur)
          if (mutatedInfo eq null) tree
          else goIdent(tree, mutatedInfo.nx.nxTransIdent(cur))
        case tree: Select =>
          implicit val mutatedInfo = mutateTransformers(info, prepForSelect, info.nx.nxPrepSelect, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val qual = transform(tree.qualifier, mutatedInfo, cur)
            goSelect(cpy.Select(tree, qual, tree.name), mutatedInfo.nx.nxTransSelect(cur))
          }
        case tree: SelectFromTypeTree =>
          implicit val mutatedInfo = mutateTransformers(info, prepForSelectFromTypeTree, info.nx.nxPrepSelectFromTypeTree, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val qual = transform(tree.qualifier, mutatedInfo, cur)
            goSelectFromTypeTree(cpy.SelectFromTypeTree(tree, qual, tree.name), mutatedInfo.nx.nxTransSelectFromTypeTree(cur))
          }
        case tree: Bind =>
          implicit val mutatedInfo = mutateTransformers(info, prepForBind, info.nx.nxPrepBind, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val body = transform(tree.body, mutatedInfo, mutatedInfo.nx.nxTransBind(cur))
            goBind(cpy.Bind(tree, tree.name, body), cur)
          }
        case tree: ValDef if !tree.isEmpty => // As a result of discussing with Martin: emptyValDefs shouldn't be copied // NAME
          implicit val mutatedInfo = mutateTransformers(info, prepForValDef, info.nx.nxPrepValDef, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val tpt = transform(tree.tpt, mutatedInfo, cur)
            val rhs = transform(tree.rhs, mutatedInfo, cur)
            goValDef(cpy.ValDef(tree, tree.mods, tree.name, tpt, rhs), mutatedInfo.nx.nxTransValDef(cur))
          }
        case tree: DefDef =>
          implicit val mutatedInfo = mutateTransformers(info, prepForDefDef, info.nx.nxPrepDefDef, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val tparams = transformSubL(tree.tparams, mutatedInfo, cur)
            val vparams = tree.vparamss.mapConserve(x => transformSubL(x, mutatedInfo, cur))
            val tpt = transform(tree.tpt, mutatedInfo, cur)
            val rhs = transform(tree.rhs, mutatedInfo, cur)
            goDefDef(cpy.DefDef(tree, tree.mods, tree.name, tparams, vparams, tpt, rhs), mutatedInfo.nx.nxTransDefDef(cur))
          }
        case tree: TypeDef =>
          implicit val mutatedInfo = mutateTransformers(info, prepForTypeDef, info.nx.nxPrepTypeDef, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val rhs = transform(tree.rhs, mutatedInfo, cur)
            goTypeDef(cpy.TypeDef(tree, tree.mods, tree.name, rhs, tree.tparams), mutatedInfo.nx.nxTransTypeDef(cur))
          }
        case _ =>
          tree
      }

    final private[TreeTransforms] def transformUnnamed(tree: Tree, info: TransformerInfo, cur: Int)(implicit ctx: Context): Tree =
      tree match {
        case tree: This =>
          implicit val mutatedInfo = mutateTransformers(info, prepForThis, info.nx.nxPrepThis, tree, cur)
          if (mutatedInfo eq null) tree
          else goThis(tree, mutatedInfo.nx.nxTransThis(cur))
        case tree: Super =>
          implicit val mutatedInfo = mutateTransformers(info, prepForSuper, info.nx.nxPrepSuper, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val qual = transform(tree.qual, mutatedInfo, cur)
            goSuper(cpy.Super(tree, qual, tree.mix), mutatedInfo.nx.nxTransSuper(cur))
          }
        case tree: Apply =>
          implicit val mutatedInfo = mutateTransformers(info, prepForApply, info.nx.nxPrepApply, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val fun = transform(tree.fun, mutatedInfo, cur)
            val args = transformSubL(tree.args, mutatedInfo, cur)
            goApply(cpy.Apply(tree, fun, args), mutatedInfo.nx.nxTransApply(cur))
          }
        case tree: TypeApply =>
          implicit val mutatedInfo = mutateTransformers(info, prepForTypeApply, info.nx.nxPrepTypeApply, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val fun = transform(tree.fun, mutatedInfo, cur)
            val args = transformL(tree.args, mutatedInfo, cur)
            goTypeApply(cpy.TypeApply(tree, fun, args), mutatedInfo.nx.nxTransTypeApply(cur))
          }
        case tree: Literal =>
          implicit val mutatedInfo = mutateTransformers(info, prepForLiteral, info.nx.nxPrepLiteral, tree, cur)
          if (mutatedInfo eq null) tree
          else goLiteral(tree, mutatedInfo.nx.nxTransLiteral(cur))
        case tree: New =>
          implicit val mutatedInfo = mutateTransformers(info, prepForNew, info.nx.nxPrepNew, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val tpt = transform(tree.tpt, mutatedInfo, cur)
            goNew(cpy.New(tree, tpt), mutatedInfo.nx.nxTransNew(cur))
          }
        case tree: Pair =>
          implicit val mutatedInfo = mutateTransformers(info, prepForPair, info.nx.nxPrepPair, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val left = transform(tree.left, mutatedInfo, cur)
            val right = transform(tree.right, mutatedInfo, cur)
            goPair(cpy.Pair(tree, left, right), mutatedInfo.nx.nxTransPair(cur))
          }
        case tree: Typed =>
          implicit val mutatedInfo = mutateTransformers(info, prepForTyped, info.nx.nxPrepTyped, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val expr = transform(tree.expr, mutatedInfo, cur)
            val tpt = transform(tree.tpt, mutatedInfo, cur)
            goTyped(cpy.Typed(tree, expr, tpt), mutatedInfo.nx.nxTransTyped(cur))
          }
        case tree: Assign =>
          implicit val mutatedInfo = mutateTransformers(info, prepForAssign, info.nx.nxPrepAssign, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val lhs = transform(tree.lhs, mutatedInfo, cur)
            val rhs = transform(tree.rhs, mutatedInfo, cur)
            goAssign(cpy.Assign(tree, lhs, rhs), mutatedInfo.nx.nxTransAssign(cur))
          }
        case tree: Block =>
          implicit val mutatedInfo = mutateTransformers(info, prepForBlock, info.nx.nxPrepBlock, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val stats = transformStats(tree.stats, mutatedInfo, cur)
            val expr = transform(tree.expr, mutatedInfo, cur)
            goBlock(cpy.Block(tree, stats, expr), mutatedInfo.nx.nxTransBlock(cur))
          }
        case tree: If =>
          implicit val mutatedInfo = mutateTransformers(info, prepForIf, info.nx.nxPrepIf, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val cond = transform(tree.cond, mutatedInfo, cur)
            val thenp = transform(tree.thenp, mutatedInfo, cur)
            val elsep = transform(tree.elsep, mutatedInfo, cur)
            goIf(cpy.If(tree, cond, thenp, elsep), mutatedInfo.nx.nxTransIf(cur))
          }
        case tree: Closure =>
          implicit val mutatedInfo = mutateTransformers(info, prepForClosure, info.nx.nxPrepClosure, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val env = transformL(tree.env, mutatedInfo, cur)
            val meth = transform(tree.meth, mutatedInfo, cur)
            val tpt = transform(tree.tpt, mutatedInfo, cur)
            goClosure(cpy.Closure(tree, env, meth, tpt), mutatedInfo.nx.nxTransClosure(cur))
          }
        case tree: Match =>
          implicit val mutatedInfo = mutateTransformers(info, prepForMatch, info.nx.nxPrepMatch, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val selector = transform(tree.selector, mutatedInfo, cur)
            val cases = transformSubL(tree.cases, mutatedInfo, cur)
            goMatch(cpy.Match(tree, selector, cases), mutatedInfo.nx.nxTransMatch(cur))
          }
        case tree: CaseDef =>
          implicit val mutatedInfo = mutateTransformers(info, prepForCaseDef, info.nx.nxPrepCaseDef, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val pat = transform(tree.pat, mutatedInfo, cur)
            val guard = transform(tree.guard, mutatedInfo, cur)
            val body = transform(tree.body, mutatedInfo, cur)
            goCaseDef(cpy.CaseDef(tree, pat, guard, body), mutatedInfo.nx.nxTransCaseDef(cur))
          }
        case tree: Return =>
          implicit val mutatedInfo = mutateTransformers(info, prepForReturn, info.nx.nxPrepReturn, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val expr = transform(tree.expr, mutatedInfo, cur)
            val from = transform(tree.from, mutatedInfo, cur)
            goReturn(cpy.Return(tree, expr, from), mutatedInfo.nx.nxTransReturn(cur))
          }
        case tree: Try =>
          implicit val mutatedInfo = mutateTransformers(info, prepForTry, info.nx.nxPrepTry, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val block = transform(tree.expr, mutatedInfo, cur)
            val handler = transform(tree.handler, mutatedInfo, cur)
            val finalizer = transform(tree.finalizer, mutatedInfo, cur)
            goTry(cpy.Try(tree, block, handler, finalizer), mutatedInfo.nx.nxTransTry(cur))
          }
        case tree: Throw =>
          implicit val mutatedInfo = mutateTransformers(info, prepForThrow, info.nx.nxPrepThrow, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val expr = transform(tree.expr, mutatedInfo, cur)
            goThrow(cpy.Throw(tree, expr), mutatedInfo.nx.nxTransThrow(cur))
          }
        case tree: SeqLiteral =>
          implicit val mutatedInfo = mutateTransformers(info, prepForSeqLiteral, info.nx.nxPrepSeqLiteral, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val elems = transformL(tree.elems, mutatedInfo, cur)
            goSeqLiteral(cpy.SeqLiteral(tree, elems), mutatedInfo.nx.nxTransLiteral(cur))
          }
        case tree: TypeTree =>
          implicit val mutatedInfo = mutateTransformers(info, prepForTypeTree, info.nx.nxPrepTypeTree, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val original = transform(tree.original, mutatedInfo, cur)
            goTypeTree(cpy.TypeTree(tree, original), mutatedInfo.nx.nxTransTypeTree(cur))
          }
        case tree: Alternative =>
          implicit val mutatedInfo = mutateTransformers(info, prepForAlternative, info.nx.nxPrepAlternative, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val trees = transformL(tree.trees, mutatedInfo, cur)
            goAlternative(cpy.Alternative(tree, trees), mutatedInfo.nx.nxTransAlternative(cur))
          }
        case tree: UnApply =>
          implicit val mutatedInfo = mutateTransformers(info, prepForUnApply, info.nx.nxPrepUnApply, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val fun = transform(tree.fun, mutatedInfo, cur)
            val implicits = transformL(tree.implicits, mutatedInfo, cur)
            val patterns = transformL(tree.patterns, mutatedInfo, cur)
            goUnApply(cpy.UnApply(tree, fun, implicits, patterns), mutatedInfo.nx.nxTransUnApply(cur))
          }
        case tree: Template =>
          implicit val mutatedInfo = mutateTransformers(info, prepForTemplate, info.nx.nxPrepTemplate, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val constr = transformSub(tree.constr, mutatedInfo, cur)
            val parents = transformL(tree.parents, mutatedInfo, cur)
            val self = transformSub(tree.self, mutatedInfo, cur)
            val body = transformStats(tree.body, mutatedInfo, cur)
            goTemplate(cpy.Template(tree, constr, parents, self, body), mutatedInfo.nx.nxTransTemplate(cur))
          }
        case tree: PackageDef =>
          implicit val mutatedInfo = mutateTransformers(info, prepForPackageDef, info.nx.nxPrepPackageDef, tree, cur)
          if (mutatedInfo eq null) tree
          else {
            val pid = transformSub(tree.pid, mutatedInfo, cur)
            val stats = transformStats(tree.stats, mutatedInfo, cur)
            goPackageDef(cpy.PackageDef(tree, pid, stats), mutatedInfo.nx.nxTransPackageDef(cur))
          }
        case Thicket(trees) if trees != Nil =>
          val trees1 = transformL(trees.asInstanceOf[List[tpd.Tree]], info, cur)
          if (trees1 eq trees) tree else Thicket(trees1)
        case tree => tree
      }

    def transform(tree: Tree, info: TransformerInfo, cur: Int)(implicit ctx: Context): Tree = {
      tree match {
        //split one big match into 2 smaller ones
        case tree: NameTree => transformNameTree(tree, info, cur)
        case tree => transformUnnamed(tree, info, cur)
      }
    }

    def transformStats(trees: List[Tree], info: TransformerInfo, current: Int)(implicit ctx: Context): List[Tree] =
      transformL(trees, info, current)(ctx)

    def transformL(trees: List[Tree], info: TransformerInfo, current: Int)(implicit ctx: Context): List[Tree] =
      flatten(trees mapConserve (x => transform(x, info, current)))

    def transformSub[Tr <: Tree](tree: Tr, info: TransformerInfo, current: Int)(implicit ctx: Context): Tr =
      transform(tree, info, current).asInstanceOf[Tr]

    def transformSubL[Tr <: Tree](trees: List[Tr], info: TransformerInfo, current: Int)(implicit ctx: Context): List[Tr] =
      transformL(trees, info, current)(ctx).asInstanceOf[List[Tr]]
  }

}
