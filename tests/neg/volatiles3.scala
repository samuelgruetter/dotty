
object v3 {
    
  trait A {
    type D <: {
      type T = String
    }
    
    lazy val d: D = ???
  }
  
  trait B {
    type D <: {
      type T = Int
    }
  }
  
  lazy val ab: A & B = ???
  
  lazy val d: ab.D = ???
  
  def int2String(i: Int): String = (i: d.T)
}

