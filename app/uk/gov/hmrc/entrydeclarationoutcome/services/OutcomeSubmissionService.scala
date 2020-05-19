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

package uk.gov.hmrc.entrydeclarationoutcome.services

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.entrydeclarationoutcome.models.OutcomeReceived
import uk.gov.hmrc.entrydeclarationoutcome.repositories.OutcomeRepo
import uk.gov.hmrc.entrydeclarationoutcome.utils.{EventLogger, SaveError, Timer}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OutcomeSubmissionService @Inject()(outcomeRepo: OutcomeRepo, override val metrics: Metrics)(
  implicit ec: ExecutionContext)
    extends Timer
    with EventLogger {

  def saveOutcome(outcome: OutcomeReceived): Future[Option[SaveError]] =
    timeFuture("Service saveOutcome", "saveOutcome.total") {
      outcomeRepo.save(outcome)
    }

}
