import sbt._
import Keys._
import Process._

object DottyBuild extends Build {

  val TRAVIS_BUILD = "dotty.travis.build"

  val defaults = Defaults.defaultSettings ++ Seq(
    // set sources to src/, tests to test/ and resources to resources/
    scalaSource in Compile := baseDirectory.value / "src",
    javaSource in Compile := baseDirectory.value / "src",
    scalaSource in Test := baseDirectory.value / "test",
    javaSource in Test := baseDirectory.value / "test",
    resourceDirectory in Compile := baseDirectory.value / "resources",
    unmanagedSourceDirectories in Compile := Seq((scalaSource in Compile).value),
    unmanagedSourceDirectories in Test := Seq((scalaSource in Test).value),
    
    // include sources in eclipse (downloads source code for all dependencies)
    //http://stackoverflow.com/questions/10472840/how-to-attach-sources-to-sbt-managed-dependencies-in-scala-ide#answer-11683728
    com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys.withSource := true,

    // to get Scala 2.11
    resolvers += Resolver.sonatypeRepo("releases"),
    
    // get reflect and xml onboard
    libraryDependencies ++= Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value,
                                "org.scala-lang.modules" %% "scala-xml" % "1.0.0-RC7"),

    // get junit onboard
    libraryDependencies += "com.novocode" % "junit-interface" % "0.9" % "test",

    // scalac options
    scalacOptions in Global ++= Seq("-feature", "-deprecation", "-language:_"),

    // enable verbose exception messages for JUnit
    testOptions += Tests.Argument(TestFrameworks.JUnit, "-a", "-v"),
    // Adjust classpath for running dotty
    mainClass in (Compile, run) := Some("dotty.tools.dotc.Main"),
    fork in run := true,
    fork in Test := true,
    // http://grokbase.com/t/gg/simple-build-tool/135ke5y90p/sbt-setting-jvm-boot-paramaters-for-scala
    javaOptions <++= (managedClasspath in Runtime, packageBin in Compile) map { (attList, bin) =>
       // put the Scala {library, reflect, compiler} in the classpath
       val path = for {
         file <- attList.map(_.data)
         path = file.getAbsolutePath
       } yield "-Xbootclasspath/p:" + path       
       // dotty itself needs to be in the bootclasspath
       val fullpath = ("-Xbootclasspath/a:" + bin) :: path.toList
       // System.err.println("BOOTPATH: " + fullpath)

       val travis_build = // propagate if this is a travis build
         if (sys.props.isDefinedAt(TRAVIS_BUILD)) 
           List(s"-D$TRAVIS_BUILD=${sys.props(TRAVIS_BUILD)}")
         else 
           List()

       travis_build ::: fullpath
    }
  )

  lazy val dotty = Project(id = "dotty", base = file("."), settings = defaults)
}
