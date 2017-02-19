name := """m8chat"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.5"

pipelineStages := Seq(digest)

doc in Compile <<= target.map(_ / "none")

resolvers += Resolver.url("Edulify Repository", url("http://edulify.github.io/modules/releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  "org.postgresql" % "postgresql" % "9.3-1103-jdbc41",
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "org.apache.commons" % "commons-email" % "1.3.3",
  "joda-time" % "joda-time" % "2.7",
  "org.joda" % "joda-convert" % "1.7",
  "commons-codec" % "commons-codec" % "1.10",
  "commons-io" % "commons-io" % "2.4",
  "com.paypal.sdk" % "paypal-core" % "1.6.6",
  "com.paypal.sdk" % "rest-api-sdk" % "1.1.2",
  "com.edulify" %% "play-hikaricp" % "2.0.2",
  "org.scalatestplus" %% "play" % "1.2.0" % "test"
)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"
