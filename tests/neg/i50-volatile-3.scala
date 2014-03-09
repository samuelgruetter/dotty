object v3 {
    
  trait A {
    type D <: {
      type T = String
    }
  }

  trait B {
    type D <: {
      type T = Int
    }
  }

  trait I extends A with B
  
  val ab: I = new I

  lazy val d: ab.D = ???

  def int2String(i: Int): String = (i: d.T)
}
