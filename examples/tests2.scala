
object tests2 {
  
  trait Animal {
    def speak: String = "hi"
  }
  
  trait Dog extends Animal {
    def speak: String = "woof"
  }
  
  trait Barry extends Dog {
    def speak: String = "wauwau"
  }
  
  trait B[A <: Animal] {
    
  }
  
  val b1: B[Int] = ???
  
  val b2: B[Animal] = ???
  
  
}
