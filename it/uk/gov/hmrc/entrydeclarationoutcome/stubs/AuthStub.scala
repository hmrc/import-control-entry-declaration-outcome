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
package uk.gov.hmrc.entrydeclarationoutcome.stubs

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.http.Status.{OK, UNAUTHORIZED}
import play.api.libs.json.{JsObject, Json}

object AuthStub {

  private val authoriseUri: String = "/auth/authorise"

  private val IcsEnrolment: JsObject = Json.obj(
    "key" -> "HMRC-ICS-ORG",
    "identifiers" -> Json.arr(
      Json.obj(
        "key"   -> "EoriTin",
        "value" -> "GB123"
      ),
      "state" -> "Activated"
    )
  )
  private val NoIcsEnrolment: JsObject = Json.obj(
    "key" -> "HMRC-MTD-IT",
    "identifiers" -> Json.arr(
      Json.obj(
        "key"   -> "MTDITID",
        "value" -> "1234567890"
      )
    )
  )

  def authorised(): StubMapping =
    stubFor(
      get(urlPathMatching(authoriseUri))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(successfulAuthResponse(IcsEnrolment).toString)))

  def authorisedNoIcsEnrolment(): StubMapping =
    stubFor(
      get(urlPathMatching(authoriseUri))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(successfulAuthResponse(NoIcsEnrolment).toString)))

  def unauthorisedNotLoggedIn(): StubMapping =
    stubFor(
      get(urlPathMatching(authoriseUri))
        .willReturn(aResponse()
          .withStatus(UNAUTHORIZED)))

  private def successfulAuthResponse(enrolments: JsObject*): JsObject =
    Json.obj("authorisedEnrolments" -> enrolments)
}
