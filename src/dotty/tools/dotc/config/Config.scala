package dotty.tools.dotc.config

object Config {
  
  private final val myBool = false

  final val cacheMembersNamed = myBool
  final val cacheAsSeenFrom = myBool
  final val useFingerPrints = myBool
  final val cacheMemberNames = myBool
  final val cacheImplicitScopes = myBool

  final val checkCacheMembersNamed = false

  final val checkConstraintsNonCyclic = true

  final val flagInstantiationToNothing = false

  /** Enable noDoubleDef checking if option "-YnoDoubleDefs" is set.
    * The reason to have an option as well as the present global switch is
    * that the noDoubleDef checking is done in a hotspot, and we do not
    * want to incur the overhead of checking an option each time.
    */
  final val checkNoDoubleBindings = true

  /** Throw an exception if a deep subtype recursion is detected */
  final val flagDeepSubTypeRecursions = true

  /** Show subtype traces for all deep subtype recursions */
  final val traceDeepSubTypeRecursions = false

  final val verboseExplainSubtype = true

  /** When set, use new signature-based matching.
   *  Advantantage of doing so: It's supposed to be faster
   *  Disadvantage: It might hide inconsistencies, so while debugging it's better to turn it off
   */
  final val newMatch = false

  /** The recursion depth for showing a summarized string */
  final val summarizeDepth = 2
}