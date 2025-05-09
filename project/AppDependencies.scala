/*
 * Copyright 2025 HM Revenue & Customs
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
import sbt.*

object AppDependencies {
  val bootstrapVersion = "9.11.0"
  val hmrcMongoVersion = "2.6.0"
  val pekkoVersion = "1.1.3"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"        % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-30" % bootstrapVersion,
    "org.typelevel"     %% "cats-core"                 % "2.13.0",
    "com.chuusai"       %% "shapeless"                 % "2.3.13"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                  %% "bootstrap-test-play-30"  % bootstrapVersion % Test,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-test-play-30" % hmrcMongoVersion % Test,
    "org.scalatestplus.play"       %% "scalatestplus-play"      % "7.0.1"          % Test,
    "org.scalamock"                %% "scalamock"               % "7.3.2"          % Test,
    "org.scalatestplus"            %% "scalacheck-1-18"         % "3.2.19.0"       % Test,
    "com.fasterxml.jackson.module" %% "jackson-module-scala"    % "2.19.0"         % Test,
  )

  val itDependencies: Seq[ModuleID] = Seq(
    "com.github.pjfanning" %% "pekko-mock-scheduler"        % "0.6.0"       % Test,
    "org.apache.pekko"     %% "pekko-testkit"               % pekkoVersion  % Test,
    "org.apache.pekko"     %% "pekko-actor-typed"           % pekkoVersion  % Test,
    "org.apache.pekko"     %% "pekko-protobuf-v3"           % pekkoVersion  % Test,
    "org.apache.pekko"     %% "pekko-serialization-jackson" % pekkoVersion  % Test,
    "org.apache.pekko"     %% "pekko-slf4j"                 % pekkoVersion  % Test,
    "org.apache.pekko"     %% "pekko-stream"                % pekkoVersion  % Test
  )
}
