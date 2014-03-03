
object test5289 {
  
  trait Food {}
  trait Apple extends Food {
    val sort: String
  }
  
  trait E {
    type T >: Food
  }
  
  trait A {
    trait D
    val de: D & E = new D with E
  }

  trait A2 extends A {
    // overriding trait D of A should be forbidden
    trait D {
      type T <: Apple
    }
  }
  
  val a2: A2 = new A2
  
  val de: a2.D & E = a2.de
  
  def downcastFoodToApple(f: Food): Apple = {
    (f: de.T): Apple
  }
  
  val myFood = new Food
  
  // myApple of type Apple references a Food (without val sort)
  val myApple: Apple = downcastFoodToApple(myFood)
  
  // runtime error!
  println(myApple.sort)
  
}

