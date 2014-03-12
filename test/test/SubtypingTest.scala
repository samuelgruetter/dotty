package test

import org.junit.{Test, Ignore}
import org.junit.Assert._

class SubtypingTest extends PhaseTest(checkAfterPhase = "frontend") {
  
  implicit class ToSubtypeJudgmentLhs(lhs: String) {
    def <:<(rhs: String) = (lhs, rhs)
  }

  // ----- infrastructure -----
  
  val name = "d3b07384d113edec49eaa6238ad5ff00"

  def preChecks(envBefore: String, t1: String, t2: String, envAfter: String): Unit = {
    try {
      checkCompiles(s"object $name {\n $envBefore \n $envAfter \n}")
    } catch {
      case e: Exception => throw new Exception("Environment doesn't compile", e)
    }
    try {
      checkCompiles(s"object $name {\n $envBefore \n (??? : $t1) \n $envAfter \n}")
    } catch {
      case e: Exception => throw new Exception("t1 doesn't compile", e)
    }
    try {
      checkCompiles(s"object $name {\n $envBefore \n (??? : $t2) \n $envAfter \n}")
    } catch {
      case e: Exception => throw new Exception("t2 doesn't compile", e)
    }
  }

  def checkSubtypeRaw(envBefore: String, t1: String, t2: String, envAfter: String): Unit = {
    try {
      checkCompiles(s"object $name {\n $envBefore \n ((??? : $t1): $t2) \n $envAfter \n}")
    } catch {
      case e: Exception => throw new Exception("t1 <: t2 doesn't hold", e)
    }
  }

  def checkNotSubtypeRaw(envBefore: String, t1: String, t2: String, envAfter: String): Unit = checkRejected(
    s"object foo {\n $envBefore \n ((??? : $t1): $t2) \n $envAfter \n}"
  )(err => err.getMessage.contains("type mismatch"))

  def checkSubtype(envBefore: String, judgment: (String, String), envAfter: String = ""): Unit = {
    preChecks(envBefore, judgment._1, judgment._2, envAfter)
    checkSubtypeRaw(envBefore, judgment._1, judgment._2, envAfter)
  }

  def checkNotSubtype(envBefore: String, judgment: (String, String), envAfter: String = ""): Unit = {
    preChecks(envBefore, judgment._1, judgment._2, envAfter)
    checkNotSubtypeRaw(envBefore, judgment._1, judgment._2, envAfter)
  }
  

  // ----- tests -----
  
  @Test def testTypeParameterBound = checkSubtype("""
    trait Foo
    def id[T <: Foo](t: T): T = {""",
      "T" <:< "Foo",
    "t }"
  )

  @Ignore // because not supported (yet?)
  @Test def testGenMethodSubtyping = checkSubtype("""
    trait Foo
    trait Bar extends Foo""",
    "{ def m[T <: Foo](x: T): T }" <:< "{ def m[T <: Bar](x: T): T }"
  )

  @Test def testAbstractTypeMemberSubtype = checkSubtype("""
    trait Foo
    trait Bar extends Foo
    trait A {
      type T <: Bar
    }
    val a = new A{}""",
    "a.T" <:< "Foo"
  )  
  
}
