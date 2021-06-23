/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationoutcome.models

import java.time.Instant

import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.WordSpec
import play.api.libs.json.Json

class FullOutcomeSpec extends WordSpec {

  val fullOutcome: FullOutcome = FullOutcome(
    OutcomeReceived(
      "eori",
      "correlationId",
      Instant.parse("2020-08-05T12:00:00.000Z"),
      Some("mrn"),
      MessageType.IE316,
      "submissionId",
      "payloadXml"
    ),
    acknowledged   = true,
    housekeepingAt = Instant.parse("2020-08-05T11:00:00.000Z")
  )

  "FullOutcome" must {
    "serialize to JSON as a flat structure" in {
      Json.toJson(fullOutcome) shouldBe Json.parse(
        """{
          |  "housekeepingAt": "2020-08-05T11:00:00.000Z",
          |  "receivedDateTime": "2020-08-05T12:00:00.000Z",
          |  "submissionId": "submissionId",
          |  "messageType": "IE316",
          |  "acknowledged": true,
          |  "correlationId": "correlationId",
          |  "outcomeXml": "payloadXml",
          |  "movementReferenceNumber": "mrn",
          |  "eori": "eori"
          |}
          |""".stripMargin
      )
    }
  }

}
