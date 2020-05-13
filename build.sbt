name := "wtw-server"

version := "0.1"

scalaVersion := "2.13.2"

libraryDependencies += "dev.zio" %% "zio" % "1.0.0-RC18-2"
libraryDependencies += "dev.zio" %% "zio-interop-cats" % "2.0.0.0-RC12"
libraryDependencies += "com.github.ghostdogpr" %% "caliban" % "0.7.7"
libraryDependencies += "com.github.ghostdogpr" %% "caliban-http4s"     % "0.7.7" // routes for http4s