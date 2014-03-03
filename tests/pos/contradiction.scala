
object test534 {
  
  trait Food {}
  trait Fruit extends Food {}
  trait Apple extends Fruit {}
  
  trait B {
    type D <: {
      type T >: Food
    }
    def d: D
  }
  trait C {
    type D <: {
      type T <: Apple
    }
    def d: D
  }
  
  val bc: B & C = new B with C {
    def d: D = ???
  }
  
  val d: bc.D = bc.d
  
  // We have d.T <: Apple, and d.T >: Food, so by transitivity, we have Food <: Apple.
  // So, from here on, the typechecker can use the assumption Food <: Apple.
  // This should not be a problem, because constructing bc will fail, so program execution
  // will never reach a scope which was typechecked under the assumption that Food <: Apple.
  
  val myFood: Food = new Food
  val asT: d.T = myFood  
  val asApple: Apple = asT
}
