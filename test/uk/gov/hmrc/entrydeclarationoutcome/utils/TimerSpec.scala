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

package uk.gov.hmrc.entrydeclarationoutcome.utils

import java.time.{Clock, Duration, Instant, ZoneOffset}

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.scalatest.Matchers.{be, convertToAnyShouldWrapper}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import org.scalatest.WordSpec
import play.api.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TimerSpec extends WordSpec with Timer with Logging {
  val metrics: Metrics   = new MockMetrics
  val startTime: Instant = Instant.now
  val endTime: Instant   = startTime.plusSeconds(1)
  val clock: Clock       = Clock.fixed(endTime, ZoneOffset.UTC)

  var timeMs: Long = _

  override def stopAndLog[A](name: String, timer: com.codahale.metrics.Timer.Context): Unit =
    timeMs = timer.stop() / 1000000

  private class MockMetrics extends Metrics {
    override val defaultRegistry: MetricRegistry = new MetricRegistry()

    override def toJson: String = throw new NotImplementedError
  }

  "Timer" must {
    val sleepMs = 300

    "Time a future correctly" in {
      await(timeFuture("test timer", "test.sleep") {
        Future.successful(Thread.sleep(sleepMs))
      })
      val beWithinTolerance = be >= sleepMs.toLong and be <= (sleepMs + 100).toLong
      timeMs should beWithinTolerance
    }

    "Time a block correctly" in {
      await(time("test timer", "test.sleep") {
        Future.successful(Thread.sleep(sleepMs))
      })
      val beWithinTolerance = be >= sleepMs.toLong and be <= (sleepMs + 100).toLong
      timeMs should beWithinTolerance
    }
    "TimeFrom calculates the time correctly" in {
      timeFrom("test timer", startTime) shouldBe Duration.between(startTime, endTime)
    }
  }
}
