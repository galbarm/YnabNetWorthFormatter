name := "YnabNetWorthFormatter"

version := "0.1"

scalaVersion := "2.13.1"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "1.3.6"
libraryDependencies += "com.google.api-client" % "google-api-client" % "1.30.9"
libraryDependencies += "com.google.oauth-client" % "google-oauth-client-jetty" % "1.30.6"
libraryDependencies += "com.google.apis" % "google-api-services-sheets" % "v4-rev610-1.25.0"