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
import uk.gov.hmrc.entrydeclarationoutcome.config.MockAppConfig
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationoutcome.models._
import uk.gov.hmrc.entrydeclarationoutcome.repositories.MockOutcomeRepo
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OutcomeRetrievalServiceSpec extends UnitSpec with MockOutcomeRepo with MockAppConfig with ScalaFutures {

  val time: Instant = Instant.now
  val clock: Clock  = Clock.fixed(time, ZoneOffset.UTC)

  val mockedMetrics: Metrics = new MockMetrics

  private class MockMetrics extends Metrics {
    override val defaultRegistry: MetricRegistry = new MetricRegistry()

    override def toJson: String = throw new NotImplementedError
  }

  val service = new OutcomeRetrievalService(outcomeRepo, mockAppConfig, clock, mockedMetrics)

  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  val submissionId             = "submissionId"
  val eori                     = "eori"
  val correlationId            = "correlationId"
  val xml                      = "somexml"
  val messageType: MessageType = MessageType.IE328

  val outcome: OutcomeReceived = OutcomeReceived(
    eori,
    correlationId,
    Instant.parse("2020-12-31T23:59:00Z"),
    None,
    messageType,
    submissionId,
    xml
  )

  val outcomeXml: OutcomeXml = OutcomeXml(xml)

  "OutcomeRetrievalService" when {
    "retrieving outcome xml by submissionId" must {
      "return the outcome payload xml if an outcome exists in the database" in {
        MockOutcomeRepo.lookupOutcomeXml(submissionId) returns Future.successful(Some(outcomeXml))

        service.retrieveOutcomeXml(submissionId).futureValue shouldBe Some(outcomeXml)
      }

      "return None if no outcome exists in the database" in {
        MockOutcomeRepo.lookupOutcomeXml(submissionId) returns Future.successful(None)

        service.retrieveOutcomeXml(submissionId).futureValue shouldBe None
      }
    }

    "retrieving outcome by eori and correlationId" must {
      "return the outcome if an outcome exists in the database" in {
        MockOutcomeRepo.lookupOutcome(eori, correlationId) returns Future.successful(Some(outcome))

        service.retrieveOutcome(eori, correlationId).futureValue shouldBe Some(outcome)
      }

      "return None if no outcome exists in the database" in {
        MockOutcomeRepo.lookupOutcome(eori, correlationId) returns Future.successful(None)

        service.retrieveOutcome(eori, correlationId).futureValue shouldBe None
      }
    }

    "acknowledging outcome xml" must {
      val newTtl = 1.day

      "return true if an outcome exists in the database" in {
        MockAppConfig.shortTtl returns newTtl
        MockOutcomeRepo.acknowledgeOutcome(eori, correlationId, time.plusMillis(newTtl.toMillis)) returns Future
          .successful(Some(outcome))

        service.acknowledgeOutcome(eori, correlationId).futureValue shouldBe Some(outcome)
      }

      "return false if no outcome exists in the database" in {
        MockAppConfig.shortTtl returns newTtl
        MockOutcomeRepo.acknowledgeOutcome(eori, correlationId, time.plusMillis(newTtl.toMillis)) returns Future
          .successful(None)

        service.acknowledgeOutcome(eori, correlationId).futureValue shouldBe None
      }
    }

    "listing outcomes xml" must {
      "return List(Outcomes) if an outcome exists in the database" in {
        MockOutcomeRepo.listOutcomes(eori) returns Future.successful(List(OutcomeMetadata("corId")))

        service.listOutcomes(eori).futureValue shouldBe List(OutcomeMetadata("corId"))
      }

      "return Empty list if no outcome exists in the database" in {
        MockOutcomeRepo.listOutcomes(eori) returns Future.successful(List.empty[OutcomeMetadata])

        service.listOutcomes(eori).futureValue shouldBe List.empty[OutcomeMetadata]
      }
    }

    "retrieving full outcome" must {
      "return the full outcome if an outcome exists in the database" in {
        val fullOutcome =
          FullOutcome(outcome, acknowledged = false, housekeepingAt = Instant.now)

        MockOutcomeRepo.lookupFullOutcome(eori, correlationId) returns Future.successful(Some(fullOutcome))

        service.retrieveFullOutcome(eori, correlationId).futureValue shouldBe Some(fullOutcome)
      }

      "return None if no outcome exists in the database" in {
        MockOutcomeRepo.lookupFullOutcome(eori, correlationId) returns Future.successful(None)

        service.retrieveFullOutcome(eori, correlationId).futureValue shouldBe None
      }
    }
  }
}
