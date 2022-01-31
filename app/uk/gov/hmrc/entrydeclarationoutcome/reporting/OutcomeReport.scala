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

package uk.gov.hmrc.entrydeclarationoutcome.reporting

import java.time.{Clock, Duration, Instant}

import play.api.libs.json.{JsNumber, JsObject}
import uk.gov.hmrc.entrydeclarationoutcome.models.{MessageType, Outcome}
import uk.gov.hmrc.entrydeclarationoutcome.reporting.audit.AuditEvent
import uk.gov.hmrc.entrydeclarationoutcome.reporting.events.{Event, EventCode}

// General purpose report for multiple event codes...
case class OutcomeReport(
  eventCode: EventCode,
  eori: String,
  correlationId: String,
  submissionId: String,
  messageType: MessageType,
  e2EDuration: Option[Duration] = None
) extends Report

object OutcomeReport {

  def apply(outcome: Outcome, eventCode: EventCode, e2eDuration: Duration): OutcomeReport =
    OutcomeReport(
      eventCode     = eventCode,
      eori          = outcome.eori,
      correlationId = outcome.correlationId,
      submissionId  = outcome.submissionId,
      messageType   = outcome.messageType,
      e2EDuration   = Some(e2eDuration)
    )

  def apply(outcome: Outcome, eventCode: EventCode): OutcomeReport =
    OutcomeReport(
      eventCode     = eventCode,
      eori          = outcome.eori,
      correlationId = outcome.correlationId,
      submissionId  = outcome.submissionId,
      messageType   = outcome.messageType
    )

  implicit val eventSources: EventSources[OutcomeReport] = new EventSources[OutcomeReport] {
    override def eventFor(clock: Clock, report: OutcomeReport): Option[Event] = {
      import report._

      val event = Event(
        eventCode      = eventCode,
        eventTimestamp = Instant.now(clock),
        submissionId   = submissionId,
        eori           = eori,
        correlationId  = correlationId,
        messageType    = messageType,
        detail         = None
      )

      Some(event)
    }

    override def auditEventFor(report: OutcomeReport): Option[AuditEvent] =
      report.eventCode match {
        case EventCode.ENS_RESP_ACK =>
          Some(AuditEvent("SubmissionAcknowledged", "ENS submission acknowledged", JsObject.empty))
        case EventCode.ENS_RESP_READY =>
          report.e2EDuration.map { duration =>
          val slaRequirementSeconds = 30
            val difference = duration.compareTo(Duration.ofSeconds(slaRequirementSeconds))
            if (difference > 0) {
              AuditEvent(
                "OutcomeReceivedGreaterThanSLA",
                "ENS Outcome Received",
                JsObject(Map("e2eTime" -> JsNumber(duration.toMillis))))
            } else {
              AuditEvent(
                "OutcomeReceivedLessThanSLA",
                "ENS Outcome Received",
                JsObject(Map("e2eTime" -> JsNumber(duration.toMillis))))
            }
          }
        case _ => None
      }
  }
}
