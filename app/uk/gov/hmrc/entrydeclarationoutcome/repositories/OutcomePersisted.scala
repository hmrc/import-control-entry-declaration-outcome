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

package uk.gov.hmrc.entrydeclarationoutcome.repositories

import play.api.libs.json._
import uk.gov.hmrc.entrydeclarationoutcome.models.{FullOutcome, MessageType, Outcome, OutcomeReceived}
import java.time.Instant
import scala.concurrent.duration._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits.jatInstantFormat

private[repositories] case class OutcomePersisted(
  eori: String,
  correlationId: String,
  acknowledged: Boolean = false,
  receivedDateTime: Instant,
  housekeepingAt: Instant,
  movementReferenceNumber: Option[String],
  messageType: MessageType,
  submissionId: String,
  outcomeXml: String)
    extends Outcome {
  def toOutcomeReceived: OutcomeReceived =
    OutcomeReceived(
      eori                    = eori,
      correlationId           = correlationId,
      movementReferenceNumber = movementReferenceNumber,
      receivedDateTime        = receivedDateTime,
      messageType             = messageType,
      submissionId            = submissionId,
      outcomeXml              = outcomeXml
    )

  def toFullOutcome: FullOutcome =
    FullOutcome(
      toOutcomeReceived,
      acknowledged   = acknowledged,
      housekeepingAt = housekeepingAt
    )
}

private[repositories] object OutcomePersisted {
  def from(outcomeReceived: OutcomeReceived, defaultTtl: FiniteDuration): OutcomePersisted = {
    import outcomeReceived._
    OutcomePersisted(
      eori                    = eori,
      correlationId           = correlationId,
      receivedDateTime        = receivedDateTime,
      housekeepingAt          = receivedDateTime.plusMillis(defaultTtl.toMillis),
      movementReferenceNumber = movementReferenceNumber,
      messageType             = messageType,
      submissionId            = submissionId,
      outcomeXml              = outcomeXml
    )
  }

  implicit val format: Format[OutcomePersisted] = Json.format[OutcomePersisted]
}
