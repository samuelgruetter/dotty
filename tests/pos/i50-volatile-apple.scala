
object v {
  
  trait Food {
    type Eater
  }
  trait Fruit extends Food
  trait Apple extends Fruit
  
  trait A {
    type D = {
      type T >: Apple <: Fruit
      val t: T
    }
    val d: D
  }
  
  lazy val a: A = ???
  
  lazy val x: a.d.t.Eater = ???
  
}
