package test

import org.junit.Test
import org.junit.Assert._

class SamplePhaseTest extends PhaseTest("frontend") {

  @Test def testTypecheckingSimpleClass = checkAccepted("class A{}") {
    (tree, context) =>
      implicit val ctx = context
        assertTrue("can typecheck simple class",
        tree.toString == "PackageDef(Ident(<empty>),List(TypeDef(Modifiers(,,List()),A,Template(DefDef(Modifiers(,,List()),<init>,List(),List(List()),TypeTree[TypeRef(ThisType(module class scala),Unit)],EmptyTree),List(Apply(Select(New(TypeTree[TypeRef(ThisType(module class lang),Object)]),<init>),List())),ValDef(Modifiers(private,,List()),_,EmptyTree,EmptyTree),List()))))"
      )
  }
  
  @Test def testBoundsInNewAreChecked = checkRejected("""
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
