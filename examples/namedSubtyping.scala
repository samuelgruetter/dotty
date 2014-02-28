object test {
  
  class A{}
  
  class B extends A
  
  def downcast(a: A): B = a match {
    case b: B => b
    case _ => ???
  }
  
  val x = downcast(new B)
  
}