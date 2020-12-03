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

package uk.gov.hmrc.entrydeclarationoutcome.reporting

import java.time.{Clock, Duration, Instant, ZoneOffset}

import org.scalatest.Assertion
import play.api.libs.json.{JsNumber, JsObject, Json}
import uk.gov.hmrc.entrydeclarationoutcome.models.MessageType
import uk.gov.hmrc.entrydeclarationoutcome.reporting.events.EventCode
import uk.gov.hmrc.entrydeclarationoutcome.utils.Values.MkValues
import uk.gov.hmrc.play.test.UnitSpec

class OutcomeReportSpec extends UnitSpec {

  val now: Instant = Instant.now
  val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

  def report(eventCode: EventCode, seconds:Int): OutcomeReport = OutcomeReport(
    eventCode     = eventCode,
    eori          = "eori",
    correlationId = "correlationId",
    submissionId  = "submissionId",
    messageType   = MessageType.IE305,
    e2EDuration = Some(Duration.ofSeconds(seconds))
  )

  "OutcomeReport" must {
    def correctJson(eventCode: EventCode): Unit = s"have the correct associated JSON event for $eventCode" in {
      val event = implicitly[EventSources[OutcomeReport]].eventFor(clock, report(eventCode, 1)).get

      Json.toJson(event) shouldBe
        Json.parse(s"""
                      |{
                      |    "eventCode" : "$eventCode",
                      |    "eventTimestamp" : "${now.toString}",
                      |    "submissionId" : "submissionId",
                      |    "eori" : "eori",
                      |    "correlationId" : "correlationId",
                      |    "messageType": "IE305"
                      |}
                      |""".stripMargin)
    }

    "all event codes" must {
      implicitly[MkValues[EventCode]].values.foreach(correctJson)
    }

    "ENS_RESP_ACK" must {
      "have the correct audit event" in {
        val event = implicitly[EventSources[OutcomeReport]].auditEventFor(report(EventCode.ENS_RESP_ACK, 1)).get

        event.auditType       shouldBe "SubmissionAcknowledged"
        event.transactionName shouldBe "ENS submission acknowledged"
        event.detail          shouldBe JsObject.empty
      }
    }

    "ENS_RESP_READY" must {
      "return an OutcomeReceivedGreaterThanSLA correct audit event" in {
        val event = implicitly[EventSources[OutcomeReport]].auditEventFor(report(EventCode.ENS_RESP_READY,31)).get

        event.auditType       shouldBe "OutcomeReceivedGreaterThanSLA"
        event.transactionName shouldBe "ENS Outcome Received"
        event.detail          shouldBe JsObject(Map("e2eTime"-> JsNumber(31000)))
      }

      "return an OutcomeReceivedLessThanSLA correct audit event" in {
        val event = implicitly[EventSources[OutcomeReport]].auditEventFor(report(EventCode.ENS_RESP_READY, 1)).get

        event.auditType       shouldBe "OutcomeReceivedLessThanSLA"
        event.transactionName shouldBe "ENS Outcome Received"
        event.detail          shouldBe JsObject(Map("e2eTime"-> JsNumber(1000)))
      }
    }
  }
}
