



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

/*
object Test {
  trait A {
    type X = String
  }
  trait B {
    type X = Int
  }
  lazy val o: A & B = ???
  def casta(o: A & B): A = o
  def castb(o: A & B): B = o
  val x: o.X = 1
  val x2: o.X = "hello"
  val x3: String = x
  def depcast(o: A): o.X = "hello"
  val y = depcast(o)
  val z: o.X = y
}
*/

/*

object vo {
  
  trait A {
    type X = String
  }
  trait B {
    type X = Int
  }

  trait C extends A with B
  
  
  //lazy val ab: A with B = ???
  
  lazy val c: C = ???
  
  /*
  def cast(s: String): Int = {
    s: ab.X
  }
  
  lazy val x: ab.X = ???
  */
}

*/
