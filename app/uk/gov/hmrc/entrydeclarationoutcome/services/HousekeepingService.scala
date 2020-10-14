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

import java.time.{Clock, Instant}

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.entrydeclarationoutcome.housekeeping.Housekeeper
import uk.gov.hmrc.entrydeclarationoutcome.models.HousekeepingStatus
import uk.gov.hmrc.entrydeclarationoutcome.repositories.{HousekeepingRepo, OutcomeRepo}
import uk.gov.hmrc.entrydeclarationoutcome.utils.{EventLogger, Timer}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class HousekeepingService @Inject()(
  outcomeRepo: OutcomeRepo,
  housekeepingRepo: HousekeepingRepo,
  override val clock: Clock,
  appConfig: AppConfig,
  override val metrics: Metrics)(implicit ec: ExecutionContext)
    extends Housekeeper
    with Timer
    with EventLogger {

  private lazy val numDeletedHistogram = metrics.defaultRegistry.histogram("housekeep-num-deleted")

  def enableHousekeeping(value: Boolean): Future[Unit] = housekeepingRepo.enableHousekeeping(value)

  def getHousekeepingStatus: Future[HousekeepingStatus] = housekeepingRepo.getHousekeepingStatus

  def setShortTtl(submissionId: String): Future[Boolean] =
    outcomeRepo.setHousekeepingAt(submissionId, nowPlusShortTtl)

  def setShortTtl(eori: String, correlationId: String): Future[Boolean] =
    outcomeRepo.setHousekeepingAt(eori, correlationId, nowPlusShortTtl)

  private def nowPlusShortTtl: Instant = clock.instant().plusMillis(appConfig.shortTtl.toMillis)

  def housekeep(): Future[Boolean] = {
    def doHouskeeping() =
      timeFuture("Housekeeping", "housekeep.total", "hkTimer") {
        outcomeRepo
          .housekeep(clock.instant)
          .andThen {
            case Success(numDeleted) => numDeletedHistogram.update(numDeleted)
          }
          .map(_ => true)
      }

    housekeepingRepo.getHousekeepingStatus.flatMap {
      case HousekeepingStatus(true) =>
        Logger.info("Running housekeeping")
        doHouskeeping()

      case HousekeepingStatus(false) =>
        Logger.info("Skipping housekeeping")
        Future.successful(false)
    }
  }
}
