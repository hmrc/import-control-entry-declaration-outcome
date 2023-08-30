/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationoutcome.controllers

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.HeaderNames._
import play.api.http.MimeTypes
import play.api.http.Status._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.{Application, Environment, Mode}

class OutcomeSubmissionControllerISpec extends AnyWordSpec with GuiceOneServerPerSuite {

  val wsClient: WSClient = app.injector.instanceOf[WSClient]

  val url = s"http://localhost:$port/import-control/outcome"

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false", "auditing.enabled" -> "false")
    .build()

  "OutcomeSubmissionController" must {

    "return BAD_REQUEST" when {
      "badly formed json payload received" in {
        val response = await(wsClient.url(url).withHttpHeaders(CONTENT_TYPE -> MimeTypes.JSON).post("not even json"))

        response.status shouldBe BAD_REQUEST
      }
    }
  }
}
