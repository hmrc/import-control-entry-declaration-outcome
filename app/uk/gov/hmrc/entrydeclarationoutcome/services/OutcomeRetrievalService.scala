/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.entrydeclarationoutcome.services

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationoutcome.models.{FullOutcome, OutcomeMetadata, OutcomeReceived, OutcomeXml}
import uk.gov.hmrc.entrydeclarationoutcome.repositories.OutcomeRepo
import uk.gov.hmrc.entrydeclarationoutcome.services.UserDetails.{CSPUserDetails, GGWUserDetails}
import uk.gov.hmrc.entrydeclarationoutcome.utils.Timer
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutcomeRetrievalService @Inject()(
  outcomeRepo: OutcomeRepo,
  appConfig: AppConfig,
  override val clock: Clock,
  override val metrics: Metrics)(implicit ec: ExecutionContext)
    extends Timer
    with Logging {
  def retrieveOutcomeXml(submissionId: String): Future[Option[OutcomeXml]] =
    outcomeRepo.lookupOutcomeXml(submissionId)

  def retrieveOutcome(eori: String, correlationId: String): Future[Option[OutcomeReceived]] =
    timeFuture("Service retrieveOutcome", "retrieveOutcome.total") {
      outcomeRepo.lookupOutcome(eori, correlationId)
    }

  def retrieveFullOutcome(eori: String, correlationId: String): Future[Option[FullOutcome]] =
    timeFuture("Service retrieveFullOutcome", "retrieveFullOutcome.total") {
      outcomeRepo.lookupFullOutcome(eori, correlationId)
    }

  def acknowledgeOutcome(eori: String, correlationId: String)(
    implicit lc: LoggingContext): Future[Option[OutcomeReceived]] =
    timeFuture("Service acknowledgeOutcome", "acknowledgeOutcome.total") {
      outcomeRepo.acknowledgeOutcome(eori, correlationId, nowPlusShortTtl)
    }

  def listOutcomes(userDetails: UserDetails): Future[List[OutcomeMetadata]] =
    timeFuture("Service listOutcomes", "listOutcomes.total") {
      userDetails match {
        case GGWUserDetails(eori) => outcomeRepo.listOutcomes(eori)
        case cspUser@CSPUserDetails(eori, _) => outcomeRepo.listOutcomes(eori, Option(cspUser.clientIdPrefix))
      }
    }

  private def nowPlusShortTtl: Instant = clock.instant().plusMillis(appConfig.shortTtl.toMillis)
}
