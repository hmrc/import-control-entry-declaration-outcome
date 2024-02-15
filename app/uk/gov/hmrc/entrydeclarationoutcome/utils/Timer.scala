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

package uk.gov.hmrc.entrydeclarationoutcome.utils

import java.time.{Duration, Instant}
import com.codahale.metrics._

import java.time.Clock
import play.api.Logging
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.{ExecutionContext, Future}

trait Timer {
  self: Logging =>

  val defaultTimerGroup: String = "timer"
  type Metric = String

  val metrics: Metrics
  val clock: Clock

  def timeFuture[A](name: String, metric: Metric, timerGroup: String = defaultTimerGroup)(block: => Future[A])(
    implicit ec: ExecutionContext): Future[A] = {
    val timer = startTimer(metric, timerGroup)
    block andThen { case _ => stopAndLog(name, timer) }
  }

  def timeFrom(metric: String, startTime: Instant): Duration = {
    val duration = Duration.between(startTime, Instant.now(clock))
    metrics.defaultRegistry.timer(metric).update(duration)
    duration
  }

  def time[A](name: String, metric: Metric, timerGroup: String = defaultTimerGroup)(block: => A): A = {
    val timer = startTimer(metric, timerGroup)
    try block
    finally stopAndLog(name, timer)
  }

  protected def startTimer(metric: Metric, timerGroup: String): Timer.Context =
    metrics.defaultRegistry.timer(s"$metric-$timerGroup").time()

  protected def stopAndLog[A](name: String, timer: Timer.Context): Unit = {
    val timeMillis = timer.stop() / 1000000

    if (timeMillis > 1000) {
      logger.info(getClass.getName + s" $name took $timeMillis ms")
    } else {
      logger.debug(getClass.getName + s" $name took $timeMillis ms")
    }
  }
}
