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

package uk.gov.hmrc.entrydeclarationoutcome.controllers.test

import java.time.Instant

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.entrydeclarationoutcome.models.{FullOutcome, MessageType, OutcomeReceived, OutcomeXml}
import uk.gov.hmrc.entrydeclarationoutcome.services.MockOutcomeRetrievalService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestOutcomeRetrievalControllerSpec extends AnyWordSpec with MockOutcomeRetrievalService {

  val controller =
    new TestOutcomeRetrievalController(Helpers.stubControllerComponents(), mockOutcomeXmlRetrievalService)

  val outcomeXml: OutcomeXml = OutcomeXml("payloadXml")
  val payloadXml             = "payloadXml"
  val submissionId           = "submissionId"
  val eori                   = "eori"
  val correlationId          = "correlationId"

  val fullOutcome: FullOutcome = FullOutcome(
    OutcomeReceived(
      eori,
      correlationId,
      Instant.now,
      None,
      MessageType.IE316,
      submissionId,
      payloadXml
    ),
    acknowledged   = true,
    housekeepingAt = Instant.now
  )

  "TestOutcomeRetrievalController" when {

    "getting outcome xml by submissionId" must {
      "return 200 with the XML" when {
        "the outcome XML could be found" in {
          MockOutcomeXmlRetrievalService.retrieveOutcome(submissionId) returns Future.successful(Some(outcomeXml))

          val result = controller.getOutcomeXmlBySubmissionId(submissionId)(FakeRequest())

          status(result)          shouldBe 200
          contentAsString(result) shouldBe payloadXml
          contentType(result)     shouldBe Some(MimeTypes.XML)
        }
      }
      "return 404" when {
        "the outcome XML could not be found" in {
          MockOutcomeXmlRetrievalService.retrieveOutcome(submissionId) returns Future.successful(None)

          val result = controller.getOutcomeXmlBySubmissionId(submissionId)(FakeRequest())

          status(result) shouldBe 404
        }
      }
    }

    "getting full outcome by eori and correlationId" must {
      "return 200 with the JSON" when {
        "the outcome could be found" in {
          MockOutcomeXmlRetrievalService.retrieveFullOutcome(eori, correlationId) returns Future.successful(
            Some(fullOutcome))

          val result = controller.getFullOutcome(eori, correlationId)(FakeRequest())

          status(result)        shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(fullOutcome)
          contentType(result)   shouldBe Some(MimeTypes.JSON)
        }
      }
      "return 404" when {
        "the outcome could not be found" in {
          MockOutcomeXmlRetrievalService.retrieveFullOutcome(eori, correlationId) returns Future.successful(None)

          val result = controller.getFullOutcome(eori, correlationId)(FakeRequest())

          status(result) shouldBe 404
        }
      }
    }
  }
}
