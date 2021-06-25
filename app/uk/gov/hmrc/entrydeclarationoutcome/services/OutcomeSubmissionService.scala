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

package uk.gov.hmrc.entrydeclarationoutcome.services

import java.time.Clock

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationoutcome.models.OutcomeReceived
import uk.gov.hmrc.entrydeclarationoutcome.repositories.OutcomeRepo
import uk.gov.hmrc.entrydeclarationoutcome.utils.{SaveError, Timer}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutcomeSubmissionService @Inject()(
  outcomeRepo: OutcomeRepo,
  override val clock: Clock,
  override val metrics: Metrics)(implicit ec: ExecutionContext)
    extends Timer
    with Logging {

  def saveOutcome(outcome: OutcomeReceived)(implicit lc: LoggingContext): Future[Option[SaveError]] =
    timeFuture("Service saveOutcome", "saveOutcome.total") {
      outcomeRepo.save(outcome)
    }

}
