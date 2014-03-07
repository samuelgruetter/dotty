
object test241 {
  
  trait A {
    def foo(i: Int): String = ???
    def foo(s: String): String = ???
  }
  
  class B extends A {
    private def foo(s: String): String = ???
  }
  
  println((new B).foo(""))
  
}
