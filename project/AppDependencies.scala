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
  val bootstrapVersion = "8.4.0"
  val hmrcMongoVersion = "1.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "org.typelevel"     %% "cats-core"                 % "2.10.0",
    "com.chuusai"       %% "shapeless"                 % "2.3.10"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"  % bootstrapVersion % "test, it",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % "test, it",
//    "com.typesafe.play"            %% "play-test"               % current          % "test",
    "org.scalatestplus.play"       %% "scalatestplus-play"      % "7.0.1"          % "test, it",
    "org.scalamock"                %% "scalamock"               % "5.2.0"          % "test, it",
    "org.scalatestplus"            %% "scalacheck-1-17"         % "3.2.18.0"       % "test, it",
    "org.wiremock"                 %  "wiremock"                % "3.3.1"          % "test, it",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"    % "2.16.1"         % "test, it",
    "com.miguno.akka"              %% "akka-mock-scheduler"     % "0.5.5"          % "test, it",
    "com.typesafe.akka"            %% "akka-testkit"            % "2.6.21"         % "test, it"
  )

  val itDependencies: Seq[ModuleID] = Seq()
}
