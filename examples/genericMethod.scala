object genericMethod {
  
  def id[T](x: T): T = {
    println(s"id($x)=$x")
    x
  }
  
  val a: Cool = id("hi")
  
  type Cool = Int | String
  
}