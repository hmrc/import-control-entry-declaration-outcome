/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import play.core.PlayVersion.current
import sbt._

object AppDependencies {
  val bootstrapVersion = "7.21.0"
  val hmrcMongoVersion = "1.3.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28" % bootstrapVersion,
    "org.typelevel"     %% "cats-core"                 % "2.10.0",
    "com.chuusai"       %% "shapeless"                 % "2.3.10"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-28"  % bootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-28" % hmrcMongoVersion % "test, it",
    "com.typesafe.play"            %% "play-test"               % current          % "test",
    "org.scalatestplus.play"       %% "scalatestplus-play"      % "5.1.0"          % "test, it",
    "org.scalamock"                %% "scalamock"               % "5.2.0"          % "test, it",
    "org.scalatestplus"            %% "scalacheck-1-17"         % "3.2.16.0"       % "test, it",
    "com.github.tomakehurst"       %  "wiremock-jre8"           % "2.35.0"         % "test, it",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"    % "2.15.2"         % "test, it",
    "com.miguno.akka"              %% "akka-mock-scheduler"     % "0.5.5"          % "test, it",
    "com.typesafe.akka"            %% "akka-testkit"            % "2.6.21"         % "test, it"
  )

  val itDependencies: Seq[ModuleID] = Seq()
}
