

object test1 {
  
  trait Foo1 {
    def hello: String
  }
  trait Foo2 {
    def hello: String
  }
  trait FooBoth extends Foo1 with Foo2 {
    
  }
  
  trait A {
    def f: Foo1
  }
  trait B {
    def f: Foo2
  }
  
  def p: A | B
  
  val a: String = p.f.hello

  
}