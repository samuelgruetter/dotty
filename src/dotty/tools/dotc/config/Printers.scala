package dotty.tools.dotc.config

object Printers {

  class Printer {
    def println(msg: => String): Unit = System.err.println(msg)
  }

  object noPrinter extends Printer {
    override def println(msg: => String): Unit = ()
  }

  val default: Printer = new Printer
  val core: Printer = new Printer
  val typr: Printer = new Printer
  val constr: Printer = new Printer
  val overload: Printer = noPrinter
  val implicits: Printer = noPrinter
  val implicitsDetailed: Printer = noPrinter
  val subtyping: Printer = new Printer
  val unapp: Printer = noPrinter
  val completions = new Printer
  val gadts = noPrinter
  val incremental = noPrinter

  val my = new Printer
}