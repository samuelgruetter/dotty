package test

import org.junit.Test
import org.junit.Assert._

class GithubIssues extends PhaseTest(checkAfterPhase = "frontend") {

  @Test def i39_1 = checkRejected("""
    object i39neg {
      trait B {
        type D <: { type T }
        def d: D
      }
      val bc: B = new B {
        def d: D = ???
        private def pd: D = ???
      }
      val d: bc.D = bc.d
      val pd: bc.D = bc.pd
      // before: infinite loop in Typer
      val asT: d.T = ???
    }
  """)(e => assertTrue(e.getMessage.contains("pd is not a member of")))

  @Test def i39_2 = checkCompiles("""
    object i39 {
      trait B {
        type D <: { type T }
        def d: D
      }
      val bc: B = new B {
        def d: D = ???
      }
      val d: bc.D = bc.d
      // before: infinite loop in Typer
      val asT: d.T = ???
    }
  """)

}
