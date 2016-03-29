name := "apidoc-generator"

organization := "com.bryzek.apidoc.generator"

scalaVersion in ThisBuild := "2.11.8"

lazy val generated = project
  .in(file("generated"))
  .enablePlugins(PlayScala)
  .settings(
    libraryDependencies ++= Seq(
      ws
    )
  )

// TODO: lib will eventually be published as a jar if it turns out
// that we need it. For now it is here mostly for reference - hoping
// we end up not needing it.
lazy val lib = project
  .in(file("lib"))
  .dependsOn(generated)
  .settings(commonSettings: _*)

lazy val generator = project
  .in(file("generator"))
  .dependsOn(scalaGenerator)
  .aggregate(scalaGenerator)
  .enablePlugins(PlayScala)
  .settings(
    routesImport += "com.bryzek.apidoc.generator.v0.Bindables._",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies ++= Seq(
      ws,
      "org.scalatestplus" %% "play" % "1.4.0-M4" % "test"
    )
  )

lazy val scalaGenerator = project
  .in(file("scala-generator"))
  .dependsOn(lib, lib % "test->test")
  .settings(commonSettings: _*)

lazy val commonSettings: Seq[Setting[_]] = Seq(
  name <<= name("apidoc-" + _),
  organization := "com.bryzek.apidoc",
  libraryDependencies ++= Seq(
    "org.atteo" % "evo-inflector" % "1.2.1",
    "com.github.mpilquist" %% "simulacrum" % "0.7.0",
    "org.scalatest" %% "scalatest" % "2.2.5" % "test",
    "org.mockito" % "mockito-all" % "1.10.19" % "test"
  ),
  scalacOptions += "-feature",
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)
