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

package uk.gov.hmrc.entrydeclarationoutcome.repositories

import org.scalamock.handlers.CallHandler
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationoutcome.models.{FullOutcome, OutcomeMetadata, OutcomeReceived, OutcomeXml}
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError

import java.time.Instant
import scala.concurrent.Future

trait MockOutcomeRepo extends MockFactory {
  val outcomeRepo: OutcomeRepo = mock[OutcomeRepo]

  object MockOutcomeRepo {
    def saveOutcome(outcome: OutcomeReceived): CallHandler[Future[Option[SaveError]]] =
      (outcomeRepo.save(_: OutcomeReceived)(_: LoggingContext)) expects (outcome, *)

    def lookupOutcomeXml(submissionId: String): CallHandler[Future[Option[OutcomeXml]]] =
      (outcomeRepo.lookupOutcomeXml(_: String)) expects submissionId

    def lookupOutcome(eori: String, correlationId: String): CallHandler[Future[Option[OutcomeReceived]]] =
      (outcomeRepo.lookupOutcome(_: String, _: String)) expects (eori, correlationId)

    def acknowledgeOutcome(
      eori: String,
      correlationId: String,
      time: Instant): CallHandler[Future[Option[OutcomeReceived]]] =
      (outcomeRepo
        .acknowledgeOutcome(_: String, _: String, _: Instant)(_: LoggingContext)) expects (eori, correlationId, time, *)

    def listOutcomes(eori: String): CallHandler[Future[List[OutcomeMetadata]]] =
      outcomeRepo.listOutcomes _ expects eori

    def setHousekeepingAt(submissionId: String, time: Instant): CallHandler[Future[Boolean]] =
      (outcomeRepo.setHousekeepingAt(_: String, _: Instant)) expects (submissionId, time)

    def setHousekeepingAt(eori: String, correlationId: String, time: Instant): CallHandler[Future[Boolean]] =
      (outcomeRepo.setHousekeepingAt(_: String, _: String, _: Instant)) expects (eori, correlationId, time)

    def lookupFullOutcome(eori: String, correlationId: String): CallHandler[Future[Option[FullOutcome]]] =
      (outcomeRepo.lookupFullOutcome(_: String, _: String)) expects (eori, correlationId)

    def housekeep(time: Instant): CallHandler[Future[Int]] =
      outcomeRepo.housekeep _ expects time
  }
}
