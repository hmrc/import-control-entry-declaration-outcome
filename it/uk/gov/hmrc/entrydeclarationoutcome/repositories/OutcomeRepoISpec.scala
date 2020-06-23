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
package uk.gov.hmrc.entrydeclarationoutcome.repositories

import java.time.Instant

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.{DefaultAwaitTimeout, Injecting}
import play.api.{Application, Environment, Mode}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationoutcome.models._
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class OutcomeRepoISpec
    extends UnitSpec
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Eventually
    with Injecting
    with IntegrationPatience {

  lazy val repository: OutcomeRepoImpl = inject[OutcomeRepoImpl]

  override def beforeAll(): Unit = {
    super.beforeAll()
    await(repository.removeAll())
    await(repository.save(acknowledgedOutcome))
    await(repository.acknowledgeOutcome(acknowledgedEori, acknowledgedCorrelationId))
  }

  override protected def afterAll(): Unit =
    super.afterAll()

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure("metrics.enabled" -> "false", "response.max.list" -> listOutcomesLimit.toString)
    .build()

  val listOutcomesLimit         = 2
  val correlationId             = "correlationId"
  val submissionId              = "submissionId"
  val eori                      = "eori"
  val acknowledgedCorrelationId = "acknowledgedCorrelationId"
  val acknowledgedSubmissionId  = "acknowledgedsubmissionId"
  val acknowledgedEori          = "acknowledgedEori"
  val outcomeXml                = "somexml"
  val receivedDateTime: Instant = Instant.parse("2020-12-31T23:59:00Z")
  val messageType: MessageType  = MessageType.IE328

  val outcome: OutcomeReceived = OutcomeReceived(
    eori,
    correlationId,
    receivedDateTime,
    Some("movementReferenceNumber"),
    messageType,
    submissionId,
    outcomeXml
  )

  val acknowledgedOutcome: OutcomeReceived = OutcomeReceived(
    acknowledgedEori,
    acknowledgedCorrelationId,
    receivedDateTime,
    Some("movementReferenceNumber"),
    messageType,
    acknowledgedSubmissionId,
    outcomeXml
  )
  val OutcomeXmlWrapper: OutcomeXml = OutcomeXml(outcomeXml)

  def lookupOutcome(submissionId: String): Option[OutcomePersisted] =
    await(repository.find("submissionId" -> submissionId)).headOption

  "OutcomeRepo" when {
    "saving an outcome" when {
      "successful" must {
        "return None" in {
          await(repository.save(outcome)) shouldBe None
        }

        "store it in the database" in {
          lookupOutcome(submissionId).map(_.toOutcomeReceived) shouldBe Some(outcome)
        }
      }

      "unique submissionId constraint violated" must {
        "return Some(SaveError.Duplicate)" in {
          val duplicate = outcome.copy(eori = "otherEori")
          await(repository.save(duplicate)) shouldBe Some(SaveError.Duplicate)
        }
      }

      "unique eori + correlationId constraint violated" must {
        "return duplicate error" in {
          val duplicate = outcome.copy(submissionId = "other")
          await(repository.save(duplicate)) shouldBe Some(SaveError.Duplicate)
        }
      }
    }

    "looking up xml by submission id" when {
      "it exists in the database and has not been acknowledged" must {
        "return it" in {
          await(repository.lookupOutcomeXml(submissionId)) shouldBe Some(OutcomeXmlWrapper)
        }
      }

      "it exists in the database and has been acknowledged" must {
        "return it" in {
          await(repository.lookupOutcomeXml(acknowledgedSubmissionId)) shouldBe Some(OutcomeXmlWrapper)
        }
      }

      "it does not exist in the database" must {
        "return None" in {
          await(repository.lookupOutcomeXml("unknownSubmissionId")) shouldBe None
        }
      }
    }

    "looking up by eori and correlation id" when {
      "it exists in the database" must {
        "return it" in {
          await(repository.lookupOutcome(eori, correlationId)) shouldBe Some(outcome)
        }
      }

      "it exists in the database and has been acknowledged" must {
        "ignore it" in {
          await(repository.lookupOutcome(acknowledgedEori, acknowledgedCorrelationId)) shouldBe None
        }
      }

      "it does not exist in the database" must {
        "return None" in {
          await(repository.lookupOutcome("unknownEori", "unknownSubmissionId")) shouldBe None
        }
      }
    }

    "acknowledging an outcome" when {
      "outcome exists and is unacknowledged" must {
        "return the updated outcome" in {
          await(repository.acknowledgeOutcome(eori, correlationId)) shouldBe Some(outcome)
        }
      }

      "outcome exists and is acknowledged" must {
        "return None" in {
          await(repository.acknowledgeOutcome(eori, correlationId)) shouldBe None
        }
      }

      "outcome does not exist" must {
        "return None" in {
          await(repository.acknowledgeOutcome("unknownEori", "unknownCorrelationId")) shouldBe None
        }
      }
    }

    "listing outcomes" when {
      val listedOutcome =
        OutcomeReceived("testEori", "corId1", receivedDateTime, None, messageType, "subId1", outcomeXml)

      "unacknowledged messages exist" should {
        "return a sequence of the messages" in {
          await(repository.removeAll())
          await(repository.save(listedOutcome))
          await(
            repository.save(listedOutcome
              .copy(correlationId = "corId2", submissionId = "subId2", movementReferenceNumber = Some("mrn"))))
          await(repository.listOutcomes("testEori")) shouldBe List(
            OutcomeMetadata("corId1"),
            OutcomeMetadata("corId2", Some("mrn")))
        }
        "return a sequence of the messages in order of the receivedDateTime" in {
          await(repository.removeAll())
          val time1 = Instant.parse("2020-12-31T23:59:00.001Z")
          val time2 = Instant.parse("2020-12-31T23:59:00.002Z")

          await(repository.save(listedOutcome.copy(receivedDateTime = time2)))
          await(
            repository.save(
              listedOutcome.copy(
                correlationId           = "corId2",
                submissionId            = "subId2",
                receivedDateTime        = time1,
                movementReferenceNumber = Some("mrn"))))
          await(repository.listOutcomes("testEori")) shouldBe List(
            OutcomeMetadata("corId2", Some("mrn")),
            OutcomeMetadata("corId1"))
        }
        "return a sequence of the messages in order of the receivedDateTime when oldest ends in 0" in {
          await(repository.removeAll())
          val time1 = Instant.parse("2020-12-31T23:59:00.000Z")
          val time2 = Instant.parse("2020-12-31T23:59:00.002Z")

          await(repository.save(listedOutcome.copy(receivedDateTime = time2)))
          await(
            repository.save(
              listedOutcome.copy(
                correlationId           = "corId2",
                submissionId            = "subId2",
                receivedDateTime        = time1,
                movementReferenceNumber = Some("mrn"))))
          await(repository.listOutcomes("testEori")) shouldBe List(
            OutcomeMetadata("corId2", Some("mrn")),
            OutcomeMetadata("corId1"))
        }
        //Limit is set to 2 in app startup
        "limit the number of messages to the value set in appConfig" in {
          await(repository.removeAll())
          await(repository.save(listedOutcome))
          await(repository.save(listedOutcome.copy(correlationId = "corId2", submissionId = "subId2")))
          await(repository.save(listedOutcome.copy(correlationId = "corId3", submissionId = "subId3")))
          await(repository.listOutcomes("testEori")).length shouldBe listOutcomesLimit
        }
        "limit the number of messages to the oldest receivedDateTime" in {
          val time1 = Instant.parse("2020-12-31T23:59:00.001Z")
          val time2 = Instant.parse("2020-12-31T23:59:00.002Z")
          val time3 = Instant.parse("2020-12-31T23:59:00.003Z")

          await(repository.removeAll())
          await(
            repository.save(
              listedOutcome.copy(correlationId = "corId1", submissionId = "subId1", receivedDateTime = time2)))
          await(
            repository.save(
              listedOutcome.copy(correlationId = "corId2", submissionId = "subId2", receivedDateTime = time3)))
          await(
            repository.save(
              listedOutcome.copy(correlationId = "corId3", submissionId = "subId3", receivedDateTime = time1)))
          await(repository.listOutcomes("testEori")) shouldBe List(OutcomeMetadata("corId3"), OutcomeMetadata("corId1"))
        }
      }
      "no unacknowledged messages exist" must {
        "return Empty List" in {
          beforeAll()
          await(repository.listOutcomes(acknowledgedEori)) shouldBe List.empty[OutcomeMetadata]
        }
      }
    }

    "housekeepingAt" must {
      val time = Instant.now.plusSeconds(60)

      "be settable" in {
        await(repository.save(outcome))                         shouldBe None
        await(repository.setHousekeepingAt(submissionId, time)) shouldBe true

        await(
          repository.collection
            .find(Json.obj("submissionId" -> submissionId), Option.empty[JsObject])
            .one[OutcomePersisted]
            .map(_.map(_.housekeepingAt.toInstant))).get shouldBe time
      }

      "return false if no submission exists" in {
        await(repository.setHousekeepingAt("unknownSubmissionId", time)) shouldBe false
      }
    }

    "expireAfterSeconds" must {
      "report on when on" in {
        await(repository.enableHousekeeping(true)) shouldBe true
        await(repository.getHousekeepingStatus)    shouldBe HousekeepingStatus.On
      }

      "be effective when on" ignore {
        await(repository.removeAll())
        await(repository.save(outcome)) shouldBe None
        repository.setHousekeepingAt(submissionId, Instant.now)

        eventually(Timeout(Span(60, Seconds))) {
          await(repository.lookupOutcomeXml(submissionId)) shouldBe None
        }
      }

      "be updatable (to turn off housekeeping)" in {
        await(repository.enableHousekeeping(false)) shouldBe true
        await(repository.getHousekeepingStatus)     shouldBe HousekeepingStatus.Off
      }

      "not be effective when off" ignore {
        await(repository.removeAll())
        await(repository.save(outcome)) shouldBe None
        repository.setHousekeepingAt(submissionId, Instant.now)

        Thread.sleep(60000)
        await(repository.lookupOutcomeXml(submissionId)) should not be None

      }

      "be updatable (to turn on housekeeping)" in {
        await(repository.enableHousekeeping(true)) shouldBe true
        await(repository.getHousekeepingStatus)    shouldBe HousekeepingStatus.On
      }
    }

  }
}
