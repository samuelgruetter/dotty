object Test {
  trait A {
    type X = String
  }
  trait B {
    type X = Int
  }
  lazy val o: A & B = ???

  def xToString(x: o.X): String = x

  def intToString(i: Int): String = xToString(i)
}
