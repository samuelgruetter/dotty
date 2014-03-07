
object shadow {

  def f(x: Int): Unit = ???
  def f(x: String): Unit = ???
  def f(x: Object): Unit = ???

  object inner {

    def f(x: Int): Int = ???
    def f(x: String): String = ???
    //def f(x: Object): Object = ???

    val x1: Int = f(1)
    val x2: String = f("")
    val x3: Object = f(new Object)

    /*
    val x1 = f(1)
    val x2 = f("")
    val x3 = f(new Object)
    */
  }
}

