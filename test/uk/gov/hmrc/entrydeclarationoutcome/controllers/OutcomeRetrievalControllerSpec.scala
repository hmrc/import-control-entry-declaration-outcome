/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.Instant

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.MimeTypes
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.entrydeclarationoutcome.models.{MessageType, OutcomeMetadata, OutcomeReceived, OutcomeXml}
import uk.gov.hmrc.entrydeclarationoutcome.reporting.events.EventCode
import uk.gov.hmrc.entrydeclarationoutcome.reporting.{MockReportSender, OutcomeReport}
import uk.gov.hmrc.entrydeclarationoutcome.services.{MockAuthService, MockOutcomeRetrievalService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.Node

class OutcomeRetrievalControllerSpec
    extends AnyWordSpec
    with MockOutcomeRetrievalService
    with MockAuthService
    with MockReportSender {

  val controller =
    new OutcomeRetrievalController(
      mockAuthService,
      Helpers.stubControllerComponents(),
      mockOutcomeXmlRetrievalService,
      mockReportSender)

  val payloadXml               = "payloadXml"
  val outcomeXml: OutcomeXml   = OutcomeXml(payloadXml)
  val submissionId             = "someSubmissionId"
  val eori                     = "someEori"
  val correlationId            = "someCorrelationId"
  val messageType: MessageType = MessageType.IE328

  val outcome: OutcomeReceived = OutcomeReceived(
    eori,
    correlationId,
    Instant.parse("2020-12-31T23:59:00Z"),
    None,
    messageType,
    submissionId,
    payloadXml
  )

  val notFoundXml: Node =
    <error>
      <code>OUTCOME_NOT_FOUND</code>
      <message>No unacknowledged outcome found</message>
    </error>

  "OutcomeRetrievalController getOutcome" must {
    "return 200 OK with the XML" when {
      "the user is authenticated and the outcome XML could be found" in {
        MockAuthService.authenticate() returns Future.successful(Some(eori))
        MockOutcomeXmlRetrievalService.retrieveOutcome(eori, correlationId) returns Future.successful(Some(outcome))

        MockReportSender.sendReport(
          OutcomeReport(EventCode.ENS_RESP_COLLECTED, eori, correlationId, submissionId, messageType))

        val result = controller.getOutcome(correlationId)(FakeRequest())

        status(result)          shouldBe OK
        contentAsString(result) shouldBe payloadXml
        contentType(result)     shouldBe Some(MimeTypes.XML)
      }
    }
    "return 404 NOT_FOUND" when {
      "the user is authenticated and the outcome XML could not be found" in {
        MockAuthService.authenticate() returns Future.successful(Some(eori))
        MockOutcomeXmlRetrievalService.retrieveOutcome(eori, correlationId) returns Future.successful(None)

        val result = controller.getOutcome(correlationId)(FakeRequest())

        status(result)                              shouldBe NOT_FOUND
        xml.XML.loadString(contentAsString(result)) shouldBe notFoundXml
        contentType(result)                         shouldBe Some(MimeTypes.XML)
      }
    }
    "return 401 UNAUTHORIZED" when {
      "the user is not-authenticated" in {
        MockAuthService.authenticate() returns Future.successful(None)

        val result = controller.getOutcome(correlationId)(FakeRequest())

        status(result) shouldBe UNAUTHORIZED
      }
    }
  }

  "OutcomeRetrievalController acknowledgeOutcome" must {
    "return 200 OK" when {
      "the user is authenticated and the outcome XML could be found" in {
        MockAuthService.authenticate() returns Future.successful(Some(eori))
        MockOutcomeXmlRetrievalService.acknowledgeOutcome(eori, correlationId) returns Future.successful(Some(outcome))

        MockReportSender.sendReport(
          OutcomeReport(EventCode.ENS_RESP_ACK, eori, correlationId, submissionId, messageType))

        val result = controller.acknowledgeOutcome(correlationId)(FakeRequest())

        status(result) shouldBe OK
      }
    }
    "return 404 NOT_FOUND" when {
      "the user is authenticated and the outcome XML could not be found" in {
        MockAuthService.authenticate() returns Future.successful(Some(eori))
        MockOutcomeXmlRetrievalService.acknowledgeOutcome(eori, correlationId) returns Future.successful(None)

        val result = controller.acknowledgeOutcome(correlationId)(FakeRequest())

        status(result)                              shouldBe NOT_FOUND
        xml.XML.loadString(contentAsString(result)) shouldBe notFoundXml
        contentType(result)                         shouldBe Some(MimeTypes.XML)
      }
    }
    "return 401 UNAUTHORIZED" when {
      "the user is not-authenticated" in {
        MockAuthService.authenticate() returns Future.successful(None)

        val result = controller.acknowledgeOutcome(correlationId)(FakeRequest())

        status(result) shouldBe UNAUTHORIZED
      }
    }
  }

  "OutcomeRetrievalController listOutcomes" must {
    "return 200 OK" when {
      "the user is authenticated and an unacknowledged outcome could be found" in {
        val corId1 = "corId1"
        val mrn    = "mrn"
        val corId2 = "corId2"
        MockAuthService.authenticate() returns Future.successful(Some(eori))
        MockOutcomeXmlRetrievalService.listOutcomes(eori) returns Future.successful(
          List(OutcomeMetadata(corId1, Some(mrn)), OutcomeMetadata(corId2)))

        val listXml =
          <entryDeclarationResponses>
            <response>
              <correlationId>{corId1}</correlationId>
              <link>/customs/imports/outcomes/{corId1}</link>
              <MRN>{mrn}</MRN>
            </response>
            <response>
              <correlationId>{corId2}</correlationId>
              <link>/customs/imports/outcomes/{corId2}</link>
            </response>
          </entryDeclarationResponses>

        val result        = controller.listOutcomes()(FakeRequest())
        val prettyPrinter = new scala.xml.PrettyPrinter(80, 4)

        status(result)                                                    shouldBe OK
        prettyPrinter.format(xml.XML.loadString(contentAsString(result))) shouldBe prettyPrinter.format(listXml)
        contentType(result)                                               shouldBe Some(MimeTypes.XML)
      }
    }
    "return 204 NO_CONTENT" when {
      "the user is authenticated and no unacknowledged outcome XML could be found" in {
        MockAuthService.authenticate() returns Future.successful(Some(eori))
        MockOutcomeXmlRetrievalService.listOutcomes(eori) returns Future.successful(List.empty[OutcomeMetadata])

        val result = controller.listOutcomes()(FakeRequest())

        status(result) shouldBe NO_CONTENT
      }
    }
    "return 401 UNAUTHORIZED" when {
      "the user is not-authenticated" in {
        MockAuthService.authenticate() returns Future.successful(None)

        val result = controller.listOutcomes()(FakeRequest())

        status(result) shouldBe UNAUTHORIZED
      }
    }
  }
}
