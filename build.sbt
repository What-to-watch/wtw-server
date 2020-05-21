enablePlugins(JavaServerAppPackaging)

name := "wtw-server"

version := "0.1"

scalaVersion := "2.13.2"

lazy val doobieVersion = "0.8.8"
lazy val calibanVersion = "0.7.7"

libraryDependencies += "dev.zio" %% "zio" % "1.0.0-RC18-2"
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC12"
// https://mvnrepository.com/artifact/dev.zio/zio-config
libraryDependencies += "dev.zio" %% "zio-config" % "1.0.0-RC18"
libraryDependencies += "com.github.ghostdogpr" %% "caliban" % calibanVersion
libraryDependencies += "com.github.ghostdogpr" %% "caliban-http4s"     % calibanVersion // routes for http4s

libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core"     % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion,
  "org.tpolecat" %% "doobie-specs2"   % doobieVersion
)