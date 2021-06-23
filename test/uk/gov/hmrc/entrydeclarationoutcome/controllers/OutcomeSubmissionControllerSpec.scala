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

package uk.gov.hmrc.entrydeclarationoutcome.controllers

import java.time.Duration

import org.scalatest.Matchers.convertToAnyShouldWrapper
import org.scalatest.WordSpec
import play.api.libs.json.Json
import play.api.test.Helpers.{contentType, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationoutcome.models.{MessageType, OutcomeReceived}
import uk.gov.hmrc.entrydeclarationoutcome.reporting.events.EventCode
import uk.gov.hmrc.entrydeclarationoutcome.reporting.{MockReportSender, OutcomeReport}
import uk.gov.hmrc.entrydeclarationoutcome.services.MockOutcomeSubmissionService
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OutcomeSubmissionControllerSpec extends WordSpec with MockOutcomeSubmissionService with MockReportSender {

  private val controller =
    new OutcomeSubmissionController(Helpers.stubControllerComponents(), mockOutcomeSubmissionService, mockReportSender)

  private val outcomeJson = Json.parse("""
                                         |{
                                         |  "eori": "someEori",
                                         |  "correlationId": "someCorrelationId",
                                         |  "submissionId": "someSubmissionId",
                                         |  "receivedDateTime": "2005-03-15T12:40:00Z",
                                         |  "outcomeXml": "someOutcomeXml",
                                         |  "messageType": "IE328"
                                         |}
                                         |""".stripMargin)

  private val outcome = outcomeJson.as[OutcomeReceived]

  private val fakeRequest = FakeRequest().withBody(outcomeJson)

  "OutcomeSubmissionController postOutcome" must {
    "return CREATED" when {
      "request is handled successfully" in {
        val e2eDuration: Duration = Duration.ofSeconds(2)

        MockReportSender.timeFrom("E2E.total-e2eTimer", outcome.receivedDateTime) returns e2eDuration
        MockOutcomeSubmissionService.saveOutcome(outcome) returns Future.successful(None)


        MockReportSender.sendReport(
          OutcomeReport(
            EventCode.ENS_RESP_READY,
            "someEori",
            "someCorrelationId",
            "someSubmissionId",
            MessageType.IE328,
            Some(e2eDuration)))

        val result = controller.postOutcome(fakeRequest)

        status(result)      shouldBe CREATED
        contentType(result) shouldBe None
      }
    }

    "return CREATED" when {
      "request is a duplicate" in {
        MockOutcomeSubmissionService.saveOutcome(outcome) returns Future.successful(Some(SaveError.Duplicate))

        val result = controller.postOutcome(fakeRequest)

        status(result)      shouldBe CREATED
        contentType(result) shouldBe None
      }
    }

    "return INTERNAL_SERVER_ERROR" when {
      "request could not be handled" in {
        MockOutcomeSubmissionService.saveOutcome(outcome) returns Future.successful(Some(SaveError.ServerError))

        val result = controller.postOutcome(fakeRequest)

        status(result)      shouldBe INTERNAL_SERVER_ERROR
        contentType(result) shouldBe None
      }
    }

    "return BAD_REQUEST" when {
      "request body cannot be read as an Outcome object" in {
        val outcomeJson = Json.parse("""
                                       |{
                                       |  "notAnOutcome": "xxx"
                                       |}
                                       |""".stripMargin)

        val fakeRequest = FakeRequest().withBody(outcomeJson)

        val result = controller.postOutcome(fakeRequest)

        status(result)      shouldBe BAD_REQUEST
        contentType(result) shouldBe None
      }
    }
  }
}
