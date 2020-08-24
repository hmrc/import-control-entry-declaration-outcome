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

import java.time.{Clock, Instant, ZoneOffset}

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationoutcome.models.{MessageType, OutcomeReceived}
import uk.gov.hmrc.entrydeclarationoutcome.repositories.MockOutcomeRepo
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OutcomeSubmissionServiceSpec extends UnitSpec with MockOutcomeRepo with ScalaFutures {

  val time: Instant = Instant.now
  val clock: Clock  = Clock.fixed(time, ZoneOffset.UTC)

  val mockedMetrics: Metrics = new MockMetrics

  private class MockMetrics extends Metrics {
    override val defaultRegistry: MetricRegistry = new MetricRegistry()

    override def toJson: String = throw new NotImplementedError
  }

  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  val service = new OutcomeSubmissionService(outcomeRepo, clock, mockedMetrics)

  val outcome: OutcomeReceived = OutcomeReceived(
    "eori",
    "correlationId",
    Instant.parse("2020-12-31T23:59:00Z"),
    Some("movementReferenceNumber"),
    MessageType.IE328,
    "submissionId",
    "outcomeXml"
  )

  "OutcomeSubmissionService" should {
    "return None" when {
      "outcome successfully stored in database" in {
        MockOutcomeRepo.saveOutcome(outcome) returns Future.successful(None)

        service.saveOutcome(outcome).futureValue shouldBe None
      }
    }

    "return Some(SaveError.ServerError)" when {
      "outcome could not be stored in database" in {
        MockOutcomeRepo.saveOutcome(outcome) returns Future.successful(Some(SaveError.ServerError))

        service.saveOutcome(outcome).futureValue shouldBe Some(SaveError.ServerError)
      }
    }

    "return Some(SaveError.Duplicate)" when {
      "outcome database already contains the record" in {
        MockOutcomeRepo.saveOutcome(outcome) returns Future.successful(Some(SaveError.Duplicate))

        service.saveOutcome(outcome).futureValue shouldBe Some(SaveError.Duplicate)
      }
    }
  }
}
