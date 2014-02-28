package hello

object world extends App {
  println("hello dotty!")

  trait A { type T <: A }
  trait B { type T <: B }

  def f(cond: Boolean) = if (cond) ((a: A) => 10) else ((b: B) => 11)
  
  trait AB extends A with B
  
  def id[T](x: T): T = {
    println(s"id($x)=$x")
    x
  }
  
  val a: Cool = id("hi")
  
  def g(cond: Boolean) = f(cond)(new AB())

  type Cool = Int | String
  
  def foo(c: Cool): Unit = c match {
    case i: Int => println("it's an Int")
    case s: String => println("it's a String")
  }
  
  foo("x")
  foo(42)

  // foo(new Object())

  object WithSealedTrait {
    sealed trait MyEnum {}
    object Yes extends MyEnum
    object No extends MyEnum
    object Maybe extends MyEnum
    
    val a: MyEnum = Yes
    val b: MyEnum = Maybe
  }
  
  object WithUnionTypes {
    object Yes {}
    object No {}
    object Maybe {}

    type MyEnum = Yes.type | No.type | Maybe.type

    val a: MyEnum = Yes
    val b: MyEnum = Maybe
    // val c: MyEnum = 42  
  }

  object IntersectionOnTraits {

    trait A { def a: Int }
    trait B { def b: Int }

    
    type AB = A & B
    def f(ab: AB): Int = ab.a + ab.b

    /* crashes
    val ab = new AB {
      def a = 43
      def b = 42
    }
    */

    val ab: AB = new A with B {
      def a = 43
      def b = 42
    }
    
    def ifFunc(b: Boolean) = if (b) new A { def a = 30 } else 566 // new B { def b = 40 }
    
    val x: B | A | Int = ifFunc(true)
    
    println(x.toString)
    
    f(ab)
  }
  
  object Test {
    
    trait Foo {
      def foo: Unit
    }
    
    trait T1 {
      type MyType <: Foo
      // type M : Nothing .. Foo
    }
    
  }
  
  /*
  object UpperBounds {
    
    val a: A
    val b: B
    
    trait A {
      type M <: b.a.M // error: cyclic reference involving type M
      val b: B
    }
    trait B {
      type M <: a.b.M
      val a: A
    }
    
  }
  */
  

}
