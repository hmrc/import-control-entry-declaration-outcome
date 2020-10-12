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
import java.util.UUID

import org.scalatest.{Assertion, BeforeAndAfterAll}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.{DefaultAwaitTimeout, Injecting}
import play.api.{Application, Environment, Mode}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.entrydeclarationoutcome.config.AppConfig
import uk.gov.hmrc.entrydeclarationoutcome.logging.LoggingContext
import uk.gov.hmrc.entrydeclarationoutcome.models._
import uk.gov.hmrc.entrydeclarationoutcome.utils.SaveError
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

class OutcomeRepoISpec
  extends UnitSpec
    with DefaultAwaitTimeout
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll
    with Eventually
    with Injecting
    with IntegrationPatience {

  val housekeepingRunLimit: Int = 20
  val housekeepingBatchSize: Int = 3

  lazy val repository: OutcomeRepoImpl = inject[OutcomeRepoImpl]

  lazy val appConfig: AppConfig = inject[AppConfig]

  override def beforeAll(): Unit = {
    super.beforeAll()
    await(repository.removeAll())
    await(repository.save(acknowledgedOutcome))
    await(repository.acknowledgeOutcome(acknowledgedEori, acknowledgedCorrelationId, Instant.now))
  }

  override protected def afterAll(): Unit =
    super.afterAll()

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .in(Environment.simple(mode = Mode.Dev))
    .configure(
      "metrics.enabled" -> "false",
      "response.max.list" -> listOutcomesLimit.toString,
      "mongodb.housekeepingRunLimit" -> housekeepingRunLimit,
      "mongodb.housekeepingBatchSize" -> housekeepingBatchSize)
    .build()

  implicit val lc: LoggingContext = LoggingContext("eori", "corrId", "subId")

  val listOutcomesLimit = 2
  val correlationId = "correlationId"
  val submissionId = "submissionId"
  val eori = "eori"
  val acknowledgedCorrelationId = "acknowledgedCorrelationId"
  val acknowledgedSubmissionId = "acknowledgedsubmissionId"
  val acknowledgedEori = "acknowledgedEori"
  val outcomeXml = "somexml"
  val receivedDateTime: Instant = Instant.parse("2020-12-31T23:59:00Z")
  val messageType: MessageType = MessageType.IE328

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

  def randomOutcomeRecieved:OutcomeReceived = OutcomeReceived(
    acknowledgedEori,
    UUID.randomUUID.toString,
    receivedDateTime,
    None,
    messageType,
    UUID.randomUUID.toString,
    outcomeXml
  )
  
  def lookupOutcome(submissionId: String): Option[FullOutcome] =
    await(repository.find("submissionId" -> submissionId)).headOption.map(_.toFullOutcome)

  "OutcomeRepo" when {
    "saving an outcome" when {
      "successful" must {
        "return None" in {
          await(repository.save(outcome)) shouldBe None
        }

        "store it in the database with the default TTL" in {
          lookupOutcome(submissionId) shouldBe Some(
            FullOutcome(
              outcome,
              acknowledged = false,
              housekeepingAt = receivedDateTime.plusMillis(appConfig.defaultTtl.toMillis)))
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

    "looking up outcome by eori and correlation id" when {
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

    "looking up full outcome by eori and correlation id" when {
      "it exists in the database" must {
        "return it" in {
          await(repository.lookupFullOutcome(eori, correlationId)) shouldBe Some(
            FullOutcome(
              outcome,
              acknowledged = false,
              housekeepingAt = receivedDateTime.plusMillis(appConfig.defaultTtl.toMillis)))
        }
      }

      "it does not exist in the database" must {
        "return None" in {
          await(repository.lookupFullOutcome("unknownEori", "unknownSubmissionId")) shouldBe None
        }
      }
    }

    "acknowledging an outcome" when {
      val time = Instant.now.plusSeconds(60)

      "outcome exists and is unacknowledged" must {
        "return the outcome" in {
          await(repository.acknowledgeOutcome(eori, correlationId, time)) shouldBe Some(outcome)
        }

        "update the state to acknowledged and set housekeepingAt" in {
          lookupOutcome(submissionId) shouldBe Some(FullOutcome(outcome, acknowledged = true, housekeepingAt = time))
        }
      }

      "outcome exists and is acknowledged" must {
        "return None" in {
          val otherTime = Instant.now.plusSeconds(120)
          await(repository.acknowledgeOutcome(eori, correlationId, otherTime)) shouldBe None
        }

        "not update the housekeepingAt time" in {
          lookupOutcome(submissionId) shouldBe Some(FullOutcome(outcome, acknowledged = true, housekeepingAt = time))
        }
      }

      "outcome does not exist" must {
        "return None" in {
          await(repository.acknowledgeOutcome("unknownEori", "unknownCorrelationId", time)) shouldBe None
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
                correlationId = "corId2",
                submissionId = "subId2",
                receivedDateTime = time1,
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
                correlationId = "corId2",
                submissionId = "subId2",
                receivedDateTime = time1,
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

    "housekeepingAt" when {
      "searching by submissionId" must {
        val time = Instant.now.plusSeconds(60)

        "be settable" in {
          await(repository.removeAll())
          await(repository.save(outcome)) shouldBe None
          await(repository.setHousekeepingAt(submissionId, time)) shouldBe true

          await(
            repository.collection
              .find(Json.obj("submissionId" -> submissionId), Option.empty[JsObject])
              .one[OutcomePersisted]
              .map(_.map(_.housekeepingAt.toInstant))).get shouldBe time
        }

        "return true if no change is made" in {
          await(repository.setHousekeepingAt(submissionId, time)) shouldBe true
          await(repository.setHousekeepingAt(submissionId, time)) shouldBe true
        }

        "return false if no submission exists" in {
          await(repository.setHousekeepingAt("unknownSubmissionId", time)) shouldBe false
        }
      }
      "searching by eori and correlationId" must {
        val time = Instant.now.plusSeconds(60)

        "be settable" in {
          await(repository.removeAll())
          await(repository.save(outcome)) shouldBe None
          await(repository.setHousekeepingAt(eori, correlationId, time)) shouldBe true

          await(
            repository.collection
              .find(Json.obj("eori" -> eori, "correlationId" -> correlationId), Option.empty[JsObject])
              .one[OutcomePersisted]
              .map(_.map(_.housekeepingAt.toInstant))).get shouldBe time
        }

        "return true if no change is made" in {
          await(repository.setHousekeepingAt(eori, correlationId, time)) shouldBe true
          await(repository.setHousekeepingAt(eori, correlationId, time)) shouldBe true
        }

        "return false if no submission exists" in {
          await(repository.setHousekeepingAt(eori, "unknownCorrelationId", time)) shouldBe false
        }
      }
    }

    "housekeep" when {
      val t0 = Instant.now

      def populateOutcomes(numOutcomes: Int): Seq[OutcomeReceived] = {
        await(repository.removeAll())
        (1 to numOutcomes).map { _ =>
          val outcome = randomOutcomeRecieved
          await(repository.save(outcome)) shouldBe None
          outcome
        }
      }

      def setHousekeepingAt(outcomes: Seq[OutcomeReceived], housekeepingTimes: Seq[Int]): Unit =
        (outcomes zip housekeepingTimes).foreach {
          case (outcome, i) =>
            await(repository.setHousekeepingAt(outcome.submissionId, t0.plusSeconds(i))) shouldBe true
        }

      def assertNotHousekept(outcome: OutcomeReceived): Assertion =
        await(repository.lookupOutcome(outcome.eori, outcome.correlationId)) should not be None

      def assertHousekept(outcome: OutcomeReceived): Assertion =
        await(repository.lookupOutcome(outcome.eori, outcome.correlationId)) shouldBe None

      "the time has reached the housekeepingAt for some documents" must {
        "delete only those documents" in {
          val numOutcomes = 10
          val outcomes    = populateOutcomes(numOutcomes)
          setHousekeepingAt(outcomes, 1 to outcomes.size)

          val elapsedSecs = 6

          await(repository.housekeep(t0.plusSeconds(elapsedSecs))) shouldBe elapsedSecs

          outcomes.take(elapsedSecs) foreach assertHousekept
          outcomes.drop(elapsedSecs) foreach assertNotHousekept
        }
      }

      "the time has not reached the housekeepingAt for any documents" must {
        "delete nothing" in {
          val numOutcomes = 10
          val outcomes    = populateOutcomes(numOutcomes)
          setHousekeepingAt(outcomes, 1 to outcomes.size)

          await(repository.housekeep(t0)) shouldBe 0

          outcomes foreach assertNotHousekept
        }
      }

      "more records than the limit require deleting" must {
        "delete only the oldest ones by housekeepingAt even if not created in that order" in {
          val numOutcomes = housekeepingRunLimit * 2
          val outcomes    = Random.shuffle(populateOutcomes(numOutcomes))

          setHousekeepingAt(outcomes, 1 to outcomes.size)

          val elapsedSecs = numOutcomes

          await(repository.housekeep(t0.plusSeconds(elapsedSecs))) shouldBe housekeepingRunLimit

          outcomes.take(housekeepingRunLimit) foreach assertHousekept
          outcomes.drop(housekeepingRunLimit) foreach assertNotHousekept
        }
      }
    }
  }
}
