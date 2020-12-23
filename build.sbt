val scala212 = "2.12.12"
val scala213 = "2.13.4"
val scalaVersions = Seq(scala212, scala213)

ThisBuild / organization := "io.latis-data"
ThisBuild / scalaVersion := scala213

val attoVersion       = "0.8.0"
val catsVersion       = "2.3.1"
val catsEffectVersion = "2.3.1"
val coursierVersion   = "2.0.8"
val fs2Version        = "2.5.0"
val http4sVersion     = "0.21.14"
val junitVersion      = "4.13.1"
val netcdfVersion     = "5.3.3"
val pureconfigVersion = "0.14.0"

lazy val commonSettings = compilerFlags ++ Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core"   % catsVersion,
    "org.typelevel" %% "cats-effect" % catsEffectVersion,
    "co.fs2"        %% "fs2-core"    % fs2Version,
    "co.fs2"        %% "fs2-io"      % fs2Version,
    "com.typesafe"   % "config"      % "1.4.1",
    "org.scalatest" %% "scalatest"   % "3.0.8" % Test
  ),
  crossScalaVersions := scalaVersions
)

lazy val compilerFlags = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "utf-8",
    "-feature",
    "-language:higherKinds"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Ypartial-unification")
    case _             => Nil
  }),
  Compile / compile / scalacOptions ++= Seq(
    "-unchecked",
    "-Xlint"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq(
      "-Ywarn-dead-code",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )
    case Some((2, 13)) => Seq(
      "-Wdead-code",
      "-Wnumeric-widen",
      "-Wvalue-discard"
    )
    case _             => Nil
  })
)

lazy val dockerSettings = Seq(
  docker / imageNames := {
    Seq(ImageName(s"${organization.value}/latis3:${version.value}"))
  },
  docker / dockerfile := {
    val mainclass = (Compile / packageBin / mainClass).value.getOrElse {
      sys.error("Expected exactly one main class")
    }
    val depClasspath = (Runtime / managedClasspath).value
    val intClasspath = (Runtime / internalDependencyAsJars).value
    val cp = (depClasspath ++ intClasspath).files.map { x =>
      s"/app/${x.getName}"
    }.mkString(":")

    new Dockerfile {
      from("openjdk:8-jre-alpine")
      expose(8080)
      entryPoint("java", "-cp", cp, mainclass)
      copy(depClasspath.files, "/app/")
      copy(intClasspath.files, "/app/")
    }
  }
)

//=== Sub-projects ============================================================

lazy val latis = project
  .in(file("."))
  .aggregate(
    core,
    `dap2-service`,
    `fdml-validator`,
    server,
    `service-interface`,
    macros,
    netcdf
  )
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true
  )

lazy val core = project
  .dependsOn(macros)
  .settings(commonSettings)
  .settings(
    name := "latis3-core",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.3.2",
      "org.scala-lang.modules" %% "scala-xml"           % "1.3.0",
      "io.circe"               %% "circe-core"          % "0.13.0",
      "org.scodec"             %% "scodec-core"         % "1.11.7",
      "org.scodec"             %% "scodec-stream"       % "2.0.0",
      "org.http4s"             %% "http4s-blaze-client" % http4sVersion,
      "org.tpolecat"           %% "atto-core"           % attoVersion,
      "com.github.regis-leray" %% "fs2-ftp"             % "0.7.0",
      "junit"                   % "junit"               % junitVersion  % Test
    )
  )

lazy val `fdml-validator` = project
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "fdml-validator"
  )

lazy val `dap2-service` = project
  .dependsOn(core)
  .dependsOn(netcdf)
  .dependsOn(`service-interface`)
  .settings(commonSettings)
  .settings(
    name := "dap2-service-interface",
    libraryDependencies ++= Seq(
      "org.http4s"     %% "http4s-core" % http4sVersion % Provided,
      "org.http4s"     %% "http4s-dsl"  % http4sVersion % Provided,
      "org.tpolecat"   %% "atto-core"   % attoVersion,
      "org.scalacheck" %% "scalacheck"  % "1.14.3" % Test,
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.14" % "1.2.5" % Test,
      "junit"           % "junit"       % junitVersion  % Test
    )
  )

lazy val python = project
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "latis3-python",
    libraryDependencies ++= Seq(
      "black.ninia" % "jep" % "3.9.0"
    )
  )

lazy val server = project
  .dependsOn(core)
  .dependsOn(`dap2-service`)
  .dependsOn(`service-interface`)
  .enablePlugins(DockerPlugin)
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(
    name := "latis3-server",
    libraryDependencies ++= Seq(
      "io.get-coursier"       %% "coursier"               % coursierVersion,
      "io.get-coursier"       %% "coursier-cats-interop"  % coursierVersion,
      "org.http4s"            %% "http4s-blaze-server"    % http4sVersion,
      "org.http4s"            %% "http4s-core"            % http4sVersion,
      "org.http4s"            %% "http4s-dsl"             % http4sVersion,
      "com.github.pureconfig" %% "pureconfig"             % pureconfigVersion,
      "com.github.pureconfig" %% "pureconfig-cats-effect" % pureconfigVersion,
      "io.chrisdavenport"     %% "log4cats-slf4j"         % "1.1.1",
      "ch.qos.logback"         % "logback-classic"        % "1.2.3" % Runtime
    ),
    // Required to suppress spurious warnings with 2.13
    scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 13)) => Seq("-Xlint:-byname-implicit")
      case _             => Nil
    })
  )

lazy val `service-interface` = project
  .settings(compilerFlags)
  .settings(
    name := "latis3-service-interface",
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-core" % http4sVersion,
      "org.typelevel" %% "cats-core"   % catsVersion,
      "org.typelevel" %% "cats-effect" % catsEffectVersion
    ),
    crossScalaVersions := scalaVersions
  )

lazy val macros = project
  .settings(commonSettings)
  .settings(
    name := "latis3-macros",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ),
    scalacOptions += "-language:experimental.macros"
  )

lazy val netcdf = project
  .dependsOn(core)
  .dependsOn(core % "test -> test")
  .settings(commonSettings)
  .settings(
    name := "latis3-netcdf",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.3.2",
      "edu.ucar"            % "cdm-core"         % netcdfVersion,
      "edu.ucar"            % "httpservices"     % netcdfVersion,
      "edu.ucar"            % "netcdf4"          % netcdfVersion,
    ),
    resolvers ++= Seq(
      "Unidata" at "https://artifacts.unidata.ucar.edu/content/repositories/unidata-releases"
    )
  )
