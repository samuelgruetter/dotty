
object test808542 {
  
  def cast[T, U](t: T): U = {
    val c = new {
      // error: lower bound T does not conform to upper bound U
      type S >: T <: U
    }
    (t: c.S)
  }
  
}
